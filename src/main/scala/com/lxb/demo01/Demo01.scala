package com.lxb.demo01

import org.apache.flink.api.scala._

object Demo01 {
  def main(args: Array[String]): Unit = {
    // 构造环境
    val env : ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    // 读取文件
    val input = "G:\\code_file\\bigData\\flinkDemo\\src\\main\\resources\\hello.txt"
    val value: DataSet[String] = env.readTextFile(input)

//    import org.apache.flink.api.scala.createTypeInformation

    val value1: AggregateDataSet[(String, Int)] = value.flatMap(_.split(" ")).map((_, 1)).groupBy(0).sum(1)

    value1.print()
  }
}
