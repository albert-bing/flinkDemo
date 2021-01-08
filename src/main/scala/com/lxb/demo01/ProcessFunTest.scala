package com.lxb.demo01

import akka.stream.actor.WatermarkRequestStrategy
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

object ProcessFunTest {
  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime)

    val dataStream: DataStream[Sensor] = env.socketTextStream("hadoop102", 7777)
      .map(element => {
        val dataArra: Array[String] = element.split(",")
        Sensor(dataArra(0), dataArra(1).toLong, dataArra(2).toDouble)
      })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[Sensor](Time.seconds(1)) {
        override def extractTimestamp(element: Sensor): Long = {
          element.currentTime
        }
      })

    val processStream: DataStream[String] = dataStream.keyBy(_.id).process(new TempUpProcess())

    dataStream.print("dataStream")

    processStream.print("processStream")

    env.execute("test")
  }

}

// 连续温度上升报警
class TempUpProcess() extends KeyedProcessFunction[String,Sensor,String]{

  // 定义一个状态，用于保存上一次的温度值
  // ValueStateDescriptor参数解释：第一个是状态的名称，第二个是要保存为状态的数据的类型
  lazy val lastTemp : ValueState[Double] = getRuntimeContext.getState(new ValueStateDescriptor[Double]("lastTemp",classOf[Double]))
  // 保存当前时间的状态，为了管理（删除）定时器 ,因为时间是时间戳，long类型的
  lazy val currentTimer : ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("currState",classOf[Long]))
  override def processElement(value: Sensor, ctx: KeyedProcessFunction[String, Sensor, String]#Context, out: Collector[String]): Unit = {
    // 取出已经保存的上一次温度状态的值
    val preTemp = lastTemp.value()
    // 更新温度状态的值
    lastTemp.update(value.temperature)
    // 获取时间戳状态的值
    val currTimer: Long = currentTimer.value()

    if (value.temperature > preTemp && currTimer == 0){
      // 获取当前的时间戳
      val currTs = ctx.timerService().currentProcessingTime() + 10000L
      ctx.timerService().registerProcessingTimeTimer(currTs)// 传入的参数为时间戳
      // 更新时间戳的状态
      currentTimer.update(currTs)
    }else if(preTemp > value.temperature || preTemp == 0.0){
      // 如果是温度是下降的或者是第一次来数据，那么就需要删掉时间戳且清空状态（非常重要）
      ctx.timerService().deleteProcessingTimeTimer(currTimer)
      currentTimer.clear()
    }

   }

  // 回调函数 --- 在设置定时器后按设定的时间触发
  // timestamp : 调用方法的时间戳，ctx：上下文  out:输出函数
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, Sensor, String]#OnTimerContext, out: Collector[String]): Unit = {
    // 可以通过ctx获取当前的key
    out.collect(ctx.getCurrentKey + "温度上升了")// 这里输出的内容需要在最上面的调用process函数的地方接收和打印一下
    // 这里也需要清除状态
    currentTimer.clear()
  }
}

//case class Sensor(id:String,currentTime:Long,temperature:Double)