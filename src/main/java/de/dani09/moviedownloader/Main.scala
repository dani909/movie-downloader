package de.dani09.moviedownloader

import java.net.URL
import java.nio.file.{Path, Paths}

import de.dani09.moviedownloader.config.{Config, ConfigParser}
import org.json.JSONException

// TODO Windows support
// TODO Tool to search by regex
// TODO CLI to change config file

object Main {
  val configPath = "./config.json"
  val movieListFileName = "movie-data.xz"
  val movieDataSource = new URL("http://verteiler1.mediathekview.de/Filmliste-akt.xz")

  def main(args: Array[String]): Unit = {
    var config: Config = null

    try {
      config = ConfigParser.parseConfig(configPath)
    } catch {
      case e: JSONException =>
        println(s"Couldn't parse $configPath: $e")
        System.exit(1)
      case e: Throwable =>
        println(s"Couldn't load $configPath: $e")
        System.exit(1)
    }

    println("Parsed Config successfully")

    println("Getting Movie List")
    val downloader = new MovieDownloader(config)
    downloader.saveMovieData(getMovieListTmpPath, movieDataSource)
    println("Downloaded Movie List")

    val allMovies = downloader.getMovieList(getMovieListTmpPath)

    println("Parsed Movie List successfully")

    val movies = allMovies
      .filter(x => config.matchesMovie(x))
      .filter(x => !downloader.isMovieAlreadyDownloaded(x))

    if (movies.nonEmpty) {
      println(s"Will download ${movies.length} Movies:")
      movies.foreach(m => println(s"${m.tvChannel} --> ${m.seriesTitle} --> ${m.episodeTitle}"))
      println()
    } else {
      println("No new Movies to download!")
    }

    movies.foreach(x => {
      println(s"[${movies.indexOf(x) + 1}/${movies.length}] Will download following Movie:")
      x.printInfo()
      downloader.downloadMovie(x)
      println()
    })

    println("Done!")
  }

  def getMovieListTmpPath: Path = {
    val tmp = System.getProperty("java.io.tmpdir")

    Paths.get(tmp, movieListFileName)
  }
}
