package gkossakowski

import dispatch._
import Defaults._
import java.io.File
import scalax.file.Path
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class CommitsHealth(buildStatusesDir: Path) {
  
  private implicit val codec = scalax.io.Codec.UTF8
  
  def buildLogFromDisk(commit: String): Option[String] = {
    val buildLogFile = buildStatusesDir / s"buildLog-$commit.txt"
    if (buildLogFile.exists) Some(buildLogFile.string) else None
  }
  
  def buildLogToDisk(commit: String, buildLog: String): Unit = {
    val buildLogFile = buildStatusesDir / s"buildLog-$commit.txt"
    buildLogFile.createFile(createParents = false, failIfExists = true)
    buildLogFile.write(buildLog)
  }
  
  def getBuildStatus(commit: String): Future[BuildStatus] = {
    buildLogFromDisk(commit) match {
      case Some(buildLog) => 
        println(s"Reading build log of $commit from disk")
        Future { readBuildStatusFromBuildLog(commit, buildLog) }
      case None => downloadBuildLog(commit)
    }
  }
  
  def readBuildStatusFromBuildLog(commit: String, buildLog: String): BuildStatus = {
    val finishedRegexp = "Finished: (.*)".r
    val finished = buildLog.lines.collect {
        case finishedRegexp(status) => status
      }
      finished.toList match {
        case Nil => sys.error("No status found in the build log")
        case status :: Nil => BuildStatus(commit, status)
        case statuses => sys.error(s"More than one status found: $statuses")
      }
  }
  
  def downloadBuildLog(commit: String): Future[BuildStatus] = {
    import dispatch._, Defaults._
    val downloadFailureFile = buildStatusesDir / s"downloadFailed-$commit.txt"
    def writeDownloadFailure(msg: String): Unit = {
      downloadFailureFile.write(msg)
    }
    def readDownloadFailure: Option[String] = {
      if (downloadFailureFile.exists) Some(downloadFailureFile.string) else None
    }
    if (downloadFailureFile.exists)
      Future.failed(new RuntimeException(downloadFailureFile.string))
    else {
      val buildLogUrl = url(s"http://scala-webapps.epfl.ch/artifacts/$commit/buildLog.txt")
      val buildLogFuture = Http(buildLogUrl OK as.String)
      // cache the result on disk
      buildLogFuture.onSuccess {
        case buildLog =>
          println(s"Download for $commit finished")
          buildLogToDisk(commit, buildLog)
      }
      buildLogFuture.onFailure {
        case ex =>
          println(s"Download for $commit failed: ${ex.getMessage}")
          downloadFailureFile.write(ex.getMessage)
      }
      buildLogFuture.map(buildLog => readBuildStatusFromBuildLog(commit, buildLog))
    }
  }
}
