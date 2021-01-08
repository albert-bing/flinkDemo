package com.lxb.demo01

import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

object TimerTest {
  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    // 从socket中读取数据
    val dataStream: DataStream[Sensor] = env.socketTextStream("hadoop102", 7777)
      .map(element => {
        val dataArra: Array[String] = element.split(",")
        // 返回一个Sensor对象
        Sensor(dataArra(0), dataArra(1).toLong, dataArra(2).toDouble)
      })
    // 测试KeyedProcessFunction
    dataStream.keyBy(_.id)
      .process(new MyProcess())
  }
}
/**
 *  <K> – Type of the key.
    <I> – Type of the input elements.
    <O> – Type of the output elements.
    里面有三个参数：K：对应的是keyby的数据类型
                I：对应的是要操作和获取元素的类型
                O:对应的是输出的数据类型
 */
class MyProcess() extends KeyedProcessFunction[String,Sensor,String]{
  // 必须实现的方法
  override def processElement(value: Sensor, ctx: KeyedProcessFunction[String, Sensor, String]#Context, out: Collector[String]): Unit = {
    ctx.timerService().registerEventTimeTimer(2000)
  }
}

//case class  Sensor(id:String,currentTime:Long,temperature:Double)