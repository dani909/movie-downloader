package de.dani09.moviedownloader

import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.file.{Files, Path}

import de.dani09.http.Http
import de.dani09.moviedownloader.config.Config
import de.dani09.moviedownloader.data.DatenFilmToMovieConverter._
import de.dani09.moviedownloader.data.Movie
import de.dani09.moviedownloader.data.ProgressBarBuilder2HttpProgressListener._
import de.mediathekview.mlib.daten.ListeFilme
import de.mediathekview.mlib.filmesuchen.{ListenerFilmeLaden, ListenerFilmeLadenEvent}
import de.mediathekview.mlib.filmlisten.FilmlisteLesen
import me.tongfei.progressbar.{ProgressBarBuilder, ProgressBarStyle}

import scala.collection.JavaConverters._
import scala.collection.parallel.ParSeq

class MovieDownloaderUtil(config: Config) {

  def saveMovieData(destination: Path, downloadUrl: URL): Unit = downloadFile(destination, downloadUrl, "Movie Data")

  def isMovieAlreadyDownloaded(movie: Movie): Boolean = {
    val path: Path = getMovieSavePath(movie)
    val file = path.toFile

    isFileUpToDate(file, movie.downloadUrl)
  }

  //noinspection SpellCheckingInspection
  def getMovieList(movieDataPath: Path): ParSeq[Movie] = {
    val l = new FilmlisteLesen() // TODO parse without Library

    var done = false
    l.addAdListener(new ListenerFilmeLaden() {
      override def fertig(e: ListenerFilmeLadenEvent): Unit = {
        super.fertig(e)
        if (e.fehler)
          println(e.text)
        done = true
      }
    })

    val filme = new ListeFilme()
    l.readFilmListe(movieDataPath.toString, filme, config.maxDaysOld)

    // Waiting until done
    while (!done) {
      Thread.sleep(100)
    }

    // Convert from "DatenFilm" to Movie
    filme.asScala
      .par
      .map(m => m.toMovie)
      .filter(_ != null)
  }

  def downloadMovie(movie: Movie): Unit = {
    val destinationPath = getMovieSavePath(movie)
    val name = s"${movie.seriesTitle} - ${movie.episodeTitle}"

    downloadFile(destinationPath, movie.downloadUrl, name, progressNameQuoted = true)
  }

  private def downloadFile(destination: Path, downloadUrl: URL, nameForProgress: String, progressNameQuoted: Boolean = false): Unit = {
    var out: FileOutputStream = null
    val taskName = if (progressNameQuoted) "\"" + nameForProgress + "\"" else nameForProgress

    try {
      Files.createDirectories(destination.getParent)
      val file = new File(destination.toUri)
      if (isFileUpToDate(file, downloadUrl)) {
        println(s"$taskName is already up-to-date")
        return
      }
      out = new FileOutputStream(file)

      println(s"Downloading $taskName")

      val listener = new ProgressBarBuilder()
        .setStyle(ProgressBarStyle.ASCII)
        .setUnit("MB", 1048576)
        .setUpdateIntervalMillis(1000)
        .showSpeed()
        .toHttpProgressListener

      Http.get(downloadUrl.toString)
        .setOutputStream(out)
        .addProgressListener(listener)
        .handleRedirects(10)
        .execute()

    } finally {
      if (out != null) {
        out.close()
      }
    }
  }

  def isFileUpToDate(file: File, url: URL): Boolean = {
    try {
      val connection = url.openConnection()
      val fullSize = connection.getContentLengthLong

      file.length() == fullSize
    } catch {
      case _: Throwable => false
    }
  }

  private def getMovieSavePath(movie: Movie) = {
    movie.getSavePath(config.downloadDirectory)
  }
}
