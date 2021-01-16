package com.lxb.demo01

//import akka.stream.actor.WatermarkRequestStrategy
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

object SideOutTest {
  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val dataStream: DataStream[Sensor] = env.socketTextStream("hadoop102", 7777)
      .map(element => {
        val dataArra: Array[String] = element.split(",")
        Sensor(dataArra(0), dataArra(1).toLong, dataArra(2).toDouble)
      })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[Sensor](Time.seconds(5)) {
        override def extractTimestamp(element: Sensor): Long = {
          element.currentTime
        }
      })

    val sideoutStream: DataStream[Sensor] = dataStream.process(new DownTempProcess())

    // 输出主输出流的内容
    sideoutStream.print("mainStream")
    // 输出侧输出流的内容
    // 注意：一定要和下面的标签名称一样
    sideoutStream.getSideOutput(new OutputTag[String]("late-date")).print("sideOutStream")

    env.execute("sideout test")

  }
}

// ProcessFunction[Sensor,String] 参数解析：第一个参数：输入的数据类型，第二个：主输出的参数的类型
class DownTempProcess() extends ProcessFunction[Sensor,Sensor]{

  // 定义一个侧输出的标签
  lazy val alertTemp:OutputTag[String] = new OutputTag[String]("late-date")

  // 实现温度低于某个值的时候报警
  override def processElement(value: Sensor, ctx: ProcessFunction[Sensor, Sensor]#Context, out: Collector[Sensor]): Unit = {
      if(value.temperature < 40){
        // 需要输出参数1：OutputTag类型的标签数据，参数2：侧输出的信息
        ctx.output(alertTemp,"低温报警："+value.id)
      }else{
        // 直接输出信息
        out.collect(value)
      }
  }
}

case class Sensor(id:String,currentTime:Long,temperature:Double)