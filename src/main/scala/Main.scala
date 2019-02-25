
import java.io.File
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.sys.process._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  val ws = StandaloneAhcWSClient()

  // The org to run this script on
  val Org = args(0)

  val GitHubToken = sys.env.getOrElse("GITHUB_TOKEN", sys.error("No GITHUB_TOKEN environment variable found")).trim

  def await[T](future: Future[T]): T = Await.result(future, 10.seconds)

  def pathRequest(path: String) = ws.url(s"https://api.github.com/$path")
    .withHttpHeaders("Authorization" -> s"token $GitHubToken")

  def urlRequest(url: String) = ws.url(url)
    .withHttpHeaders("Authorization" -> s"token $GitHubToken")

  val LinkUrlRegex = " *<(.*)> *".r
  val RelRegex = " *rel=\"(.*)\" *".r

  def extractNextLink(value: String): Option[String] = {
    value.split(",")
      .map(_.split(";").toList)
      .collect {
        case LinkUrlRegex(url) :: params =>
          url -> params.collectFirst {
            case RelRegex("next") => true
          }.getOrElse(false)
      }.collectFirst {
        case (url, true) => url
      }
  }

  def allPages[T: Reads](request: StandaloneWSRequest): List[T] = {
    println(s"Requesting ${request.uri}")
    val result = await(request.get())
    if (result.status >= 400) {
      println(result.body)
      sys.error(s"Error response status: ${result.status}")
    }
    val content = result.body[JsValue].as[List[T]]
    result.header("Link").flatMap(extractNextLink) match {
      case None => content
      case Some(next) => content ::: allPages(urlRequest(next))
    }
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursively)
    }
    file.delete()
  }

  /**
    * Returns true if there is more than one file in the wiki, or if the one file in the wiki contains
    * more than 100 bytes.
    */
  def checkWikiIsUsed(org: String, repo: String): Boolean = {
    // Clone to a temp directory
    val dir = Files.createTempDirectory(s"$repo").toFile
    try {
      val rc = s"git clone git@github.com:$org/$repo.wiki.git ${dir.getAbsolutePath}" ! ProcessLogger(_ => ())
      if (rc == 128) {
        // If there are no files in the wiki, GitHub will report that theres no repo and git will return 128
        false
      } else {
        val files = dir.list().toSeq.filterNot(_ == ".git")
        if (files == Seq("Home.md")) {
          // See if it contains more than 100 bytes
          Files.readAllBytes(new File(dir, "Home.md").toPath).length > 100
        } else {
          true
        }
      }
    } finally {
      deleteRecursively(dir)
    }
  }

  try {

    val wikis = allPages[Repo](pathRequest(s"orgs/$Org/repos")
      .withQueryStringParameters("per_page" -> "100", "type" -> "public")
    )
      .filterNot(_.archived)
      .filter(_.has_wiki)
      .map {
        case unused @ Repo(name, _, _) if !checkWikiIsUsed(Org, name) =>
          val resp = await(pathRequest(s"repos/$Org/$name")
            .patch(Json.obj(
              "name" -> name,
              "has_wiki" -> false
            ))
          )
          if (resp.status >= 300) {
            println(resp.body)
            sys.error(s"Error disabling wiki on $Org/$name, got response ${resp.status}")
          }
          println(s"Disabled wiki in repo $Org/$name")
          unused.copy(has_wiki = false)
        case used => used
      }.filter(_.has_wiki)

    if (wikis.nonEmpty) {
      println()
      println("The following wikis have been left untouched due to potentially containing content:")
      wikis.foreach(repo => println(s"https://github.com/$Org/${repo.name}/wiki"))
    }

  } finally {
    ws.close()
    system.terminate()
  }

}

case class Repo(name: String, has_wiki: Boolean, archived: Boolean)

object Repo {
  implicit val reads: Reads[Repo] = Json.reads
}
