package com.cloudlabhk.CiMarking


import scala.sys.process._

class Main {

  import java.io._
  import java.net.URLDecoder

  import com.amazonaws.services.lambda.runtime.events.S3Event
  import com.amazonaws.services.s3.AmazonS3Client
  import com.amazonaws.services.s3.model.GetObjectRequest

  val sourceBucket = "markingaccelerator-cloudlabhk-com"
  val mavenZipFileName = "apache-maven-3.3.9-bin.zip"

  val jdkFileName = "jdk-8u101-linux-x64.tar.gz"

  val projectZipFileName = "ite3101.zip"
  val pathScript = "path.sh"
  val tmp = "/tmp/"
  val mavenFolder = tmp + "maven"
  val projectFolder = tmp + "project"

  def decodeS3Key(key: String): String = URLDecoder.decode(key.replace("+", " "), "utf-8")

  def processNewCode(event: S3Event): Unit = {
    //val result = event.getRecords.asScala.map(record => decodeS3Key(record.getS3.getObject.getKey)).asJava

    val s3Client = new AmazonS3Client
    val zipArchive = new ZipArchive

    def replaceFileNameForFolder(s: String) = s.replaceAll(".zip", "")
      .replaceAll("-bin", "")       //maven folder
      .replaceAll("_solution", "")  //Test with solution

    def prepareBuildTools: Unit = {
      val f1 = (mavenZipFileName, mavenFolder)
      val f2 = (projectZipFileName, projectFolder)
      List(f1, f2).par.foreach { case (zipFileName, unZipFolder) => {
        val zipFile = new File(tmp + zipFileName)
        s3Client.getObject(new GetObjectRequest(sourceBucket, zipFileName), zipFile)
        zipArchive.unZip(tmp + zipFileName, tmp)
        new File(tmp + replaceFileNameForFolder(zipFileName)).renameTo(new File(unZipFolder))
      }
      }
    }
    //Remove the old project code!
    println(s"rm -rf $projectFolder" !)
    prepareBuildTools

    val jdkFolder = new File(tmp + "jdk")
    if (!jdkFolder.exists()) {
      //for the case of container reuse
      val s3Object = s3Client.getObject(new GetObjectRequest(sourceBucket, jdkFileName))
      TarUtils.createDirectoryFromTarGz(s3Object.getObjectContent, jdkFolder)
    }

    println(s"chmod -R 777 $tmp/jdk/jdk1.8.0_101" !)
    println(s"chmod -R 777 $mavenFolder/bin/" !)

    println(s"env JAVA_HOME=/tmp/jdk/jdk1.8.0_101 $mavenFolder/bin/mvn -Dmaven.repo.local=/tmp/repo -v" !)
    //println(s"ls -al $projectFolder" !)
    val testClass = "hk.edu.vtc.it.ite3101.lab03.MinimumCoinsTest"
    println(s"env JAVA_HOME=/tmp/jdk/jdk1.8.0_101 $mavenFolder/bin/mvn -Dmaven.repo.local=/tmp/repo -q -f $projectFolder -Dtest=$testClass test surefire-report:report" !)
    println(s"ls -al $projectFolder/target/surefire-reports/" !)
    println(s"cat $projectFolder/target/surefire-reports/$testClass.txt" !)
    println(s"cat $projectFolder/target/surefire-reports/TEST-$testClass.xml" !)
  }
}