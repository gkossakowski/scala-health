package gkossakowski

import dispatch._
import Defaults._
import java.io.File
import scalax.file.Path
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main {
  private val buildStatusesDir: Path = {
    val dir = Path("build-statuses").toAbsolute
    println(dir.toAbsolute)
    dir.createDirectory(createParents = false, failIfExists = false)
    dir
  }
  
  private case class HealthSummary(commits: Seq[String], statusesCounts: Map[String, Int])
  
  private def summaryOfCommitsHealth(commits: Seq[String]): HealthSummary = {
    val commitHealth = new CommitsHealth(buildStatusesDir)
    val statusFutures = commits.map { commit => 
      commitHealth.getBuildStatus(commit).recover { case _ => BuildStatus(commit, "UNKNOWN") }
    }
    val statusesFuture = Future.sequence(statusFutures)
    import scala.concurrent.duration._
    val statuses =  Await.result(statusesFuture, Duration(60, SECONDS))
    val statusesCounts = statuses.groupBy(_.status).mapValues(_.size)
    HealthSummary(commits, statusesCounts)
  }
  
	def main(args: Array[String]): Unit = {
	  // Scala 2.10.x
	  val scala210Summary = {
	    // generated with git rev-list cc9871f8dd4f685660976f1a6e5e07c28a4c53a7..v2.10.0
	    // the cc9871f8dd4f685660976f1a6e5e07c28a4c53a7 is the first commit we have
	    // a build status recorded for
	    val commitsFile = Path("commits-2.10.x")
	    val commits = commitsFile.lines()
	    summaryOfCommitsHealth(commits.toSeq)
	  }

	  // Scala 2.11.x
	  val scala211Summary = {
	    // generated with git rev-list v2.11.0
      val commitsFile = Path("commits-2.11.x")
      val commits = commitsFile.lines()
      summaryOfCommitsHealth(commits.toSeq)
    }

	  println(s"Scala 2.10.x build statuses: ${scala210Summary.statusesCounts}")
	  println(s"Scala 2.11.x build statuses: ${scala211Summary.statusesCounts}")
	}
}
