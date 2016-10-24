package com.cloudlabhk.CiMarking


import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.transfer.TransferManager

import scala.io.Source
import scala.sys.process._

class MainGradle {

  import java.io._
  import java.net.URLDecoder

  import com.amazonaws.services.lambda.runtime.events.S3Event
  import com.amazonaws.services.s3.AmazonS3Client
  import com.amazonaws.services.s3.model.GetObjectRequest

  import scala.collection.JavaConverters._

  val sourceBucket = "markingaccelerator-cloudlabhk-com"
  val resultBucket = "cimarking-cloudlabhk.com"
  val jdkFileName = "jdk-8u101-linux-x64.tar.gz"

  val projectZipFileName = "MultipleChoicesTestMarking.zip"
  val tmp = "/tmp/"
  val projectFolder = tmp + "project"


  def decodeS3Key(key: String): String = URLDecoder.decode(key.replace("+", " "), "utf-8")

  def processNewCode(event: S3Event): String = {
    val s3Client = new AmazonS3Client
    val zipArchive = new ZipArchive

    val bucket = event.getRecords.asScala.map(record => decodeS3Key(record.getS3.getBucket.getName)).head
    val key = event.getRecords.asScala.map(record => decodeS3Key(record.getS3.getObject.getKey)).head

    val prefix = key.replace("/McMarker.java", "")
    val codeFileName = key.split("/").last
    val codeTestFileName = codeFileName.replace(".java", "Test.java")


    val codeFile = new File(tmp + codeFileName)
    s3Client.getObject(new GetObjectRequest(bucket, key), codeFile)
    val packageLine = Source.fromFile(tmp + codeFileName).getLines().find(s => s.contains("package")).getOrElse("")

    if (packageLine.isEmpty) {
      return "Invalid code without package declaration!"
    }
    val packageName = packageLine.replace("package", "").replaceAll(";", "").trim
    val packageFolder = packageName.replaceAll("\\.", "/")

    def replaceFileNameForFolder(s: String) = s.replaceAll(".zip", "")
      .replaceAll("-bin", "") //maven folder

    def downloadAndUnzip(zipFileName: String, unZipFolder: String): Unit = {
      val zipFile = new File(tmp + zipFileName)
      s3Client.getObject(new GetObjectRequest(sourceBucket, zipFileName), zipFile)
      zipArchive.unZip(tmp + zipFileName, tmp)
      zipFile.delete()
      new File(tmp + replaceFileNameForFolder(zipFileName)).renameTo(new File(unZipFolder))
    }

    def setupJdk: Unit = {
      val jdkFolder = new File(tmp + "jdk")
      if (!jdkFolder.exists()) {
        //for the case of container reuse
        val s3Object = s3Client.getObject(new GetObjectRequest(sourceBucket, jdkFileName))
        TarUtils.createDirectoryFromTarGz(s3Object.getObjectContent, jdkFolder)
        println(s"du -sh /tmp/jdk/jdk1.8.0_101/" !)
        //println(s"rm -rf $JAVA_HOME/src.zip $JAVA_HOME/javafx-src.zip $JAVA_HOME/man" !)
        println(s"rm -rf /tmp/jdk/jdk1.8.0_101/*src.zip " +
          s"/tmp/jdk/jdk1.8.0_101/lib/missioncontrol " +
          s"/tmp/jdk/jdk1.8.0_101/lib/visualvm " +
          s"/tmp/jdk/jdk1.8.0_101/lib/*javafx* " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/plugin.jar " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/ext/jfxrt.jar " +
          s"/tmp/jdk/jdk1.8.0_101/jre/bin/javaws " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/javaws.jar " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/desktop " +
          s"/tmp/jdk/jdk1.8.0_101/jre/plugin " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/deploy* " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/*javafx* " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/*jfx* " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libdecora_sse.so " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libprism_*.so " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libfxplugins.so " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libglass.so " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libgstreamer-lite.so " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libjavafx*.so " +
          s"/tmp/jdk/jdk1.8.0_101/jre/lib/amd64/libjfx*.so" !)
        println(s"du -sh /tmp/jdk/jdk1.8.0_101/" !)
      }
      println(s"chmod -R 777 $tmp/jdk/jdk1.8.0_101" !)
    }
    def runTest: Unit = {
      println(s"du -sh $tmp" !)
      println(s"env JAVA_HOME=$tmp/jdk/jdk1.8.0_101 env GRADLE_USER_HOME=$tmp $projectFolder/gradlew test -p $projectFolder" !)
      println(s"du -sh $tmp" !)
    }

    def uploadTestResult: Unit = {
      val credentialProviderChain = new DefaultAWSCredentialsProviderChain
      val tx = new TransferManager(credentialProviderChain.getCredentials)
      val testUpload = tx.uploadDirectory(resultBucket, s"$prefix/test/", new File("/tmp/project/build/reports/tests/test/"), true)
      testUpload.waitForCompletion
      val testResultUpload = tx.uploadDirectory(resultBucket, s"$prefix/test-results/", new File("/tmp/project/build/test-results/"), true)
      testResultUpload.waitForCompletion
      tx.shutdownNow
      println("Upload test result completed")
    }

    //Remove the old project code!
    println(s"rm -rf $projectFolder" !)
    downloadAndUnzip(projectZipFileName, projectFolder)

    val testFile = new File(s"$projectFolder/src/test/java/$packageFolder/$codeTestFileName")
    println(testFile)
    if (!testFile.exists()) {
      return s"No test found for $codeFileName"
    }

    println(s"cp $codeFile $projectFolder/src/main/java/$packageFolder/" !)
    println(s"chmod -R 777 $projectFolder" !)
    println(s"rm -rf $projectFolder/target/" !)
    println(s"rm -rf $projectFolder/build/" !)

    setupJdk
    runTest
    uploadTestResult

    "OK"
  }
}
