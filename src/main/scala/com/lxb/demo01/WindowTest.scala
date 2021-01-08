package com.lxb.demo01

import com.lxb.demo01
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.{TimeCharacteristic, functions}
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, timestamps}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time

object WindowTest {
  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置处理时间
    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime)
    // 设置水印的生成时间间隔:毫秒级别的long类型
    env.getConfig.setAutoWatermarkInterval(100L)

    // 从文件中读取
    val dataStream: DataStream[Sensor] = env.readTextFile("G:\\code_file\\bigData\\flinkDemo\\src\\main\\resources\\sensor.txt").map(element => {
      val dataArra: Array[String] = element.split(",")
      Sensor(dataArra(0).trim, dataArra(1).trim.toLong, dataArra(2).trim.toDouble)
    })
    // 从端口中读取
    val dataStream2: DataStream[Sensor] = env.socketTextStream("hadoop102", 7777).map(element => {
      val dataArra: Array[String] = element.split(",")
      Sensor(dataArra(0).trim, dataArra(1).trim.toLong, dataArra(2).toDouble)
    })
//      .assignAscendingTimestamps(_.currentTime) // 简单实现：针对有序数据，只需要指定是哪个字段即可。所需要的的参数的毫秒级的。
//      .assignTimestampsAndWatermarks(new MyAssigner()) // 设置水印-周期性
//      .assignTimestampsAndWatermarks(new MyAssigner2()) // 设置水印-指定型
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[Sensor](Time.seconds(1)) {
        override def extractTimestamp(t: Sensor): Long = {
          t.currentTime // 返回一个毫秒级
        }
      })

    // 对数据进行处理
    val minTempPerWindowStream: DataStream[(String, Double)] = dataStream2.map(element => {
      (element.id, element.temperature)
    })
      .keyBy(_._1)
      .timeWindow(Time.seconds(10))
      .reduce((data1, data2) => (data1._1, data2._2.min(data1._2)))// 使用reduce将WindowStream数据转化为dataStream

    dataStream2.print("datastream2 test")

    minTempPerWindowStream.print("mindata test")

    env.execute("window test")


  }
}

class MyAssigner() extends AssignerWithPeriodicWatermarks[Sensor]{// 周期性生成的水印，还需要在最开始设置水印的生成时间段
  val bound = 60000 // 设置延迟的时间为：一秒
  var maxTs = Long.MinValue // 设置最大值
  override def getCurrentWatermark: Watermark = new Watermark(maxTs - bound) // 这里减的原因是：最大时间点-时间延迟=要设置水印的时间点

  // 抽取时间戳：即指定哪个字段为时间戳
  override def extractTimestamp(t: Sensor, l: Long): Long = {
    maxTs = maxTs.max(t.currentTime)// 变更水印
    t.currentTime // 指定水印的字段
  }
}

class MyAssigner2() extends AssignerWithPunctuatedWatermarks[Sensor]{
  // 两个参数：第一个是每次使用的数据 第二个是：提取出来的时间戳
  override def checkAndGetNextWatermark(t: Sensor, l: Long): Watermark = new Watermark(l) // 可以直接使用提取的时间戳来返回一个水印，实现每次都设置一个水印

  override def extractTimestamp(t: Sensor, l: Long): Long = t.currentTime// 指定水印
}

//case class  Sensor(id:String,currentTime:Long,temperature:Double)