package io.sebi.api

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.Feed
import io.sebi.downloader.IntoMediaLibraryDownloader


fun Route.feedApi(feed: Feed, downloader: IntoMediaLibraryDownloader) {
    with(feed) {
        application.updateFeed() // TODO: This binds it to the application's coroutinescope. That's okay, but there's probably a better place to do it than here.
    }
    route("feed") {
        get {
            call.respond(feed.getFeed())
        }
        post("accept") {
            val uuid = call.receive<String>()
            val item = feed.accept(uuid)
            downloader.download(item!!.originUrl)
        }
        post("decline") {
            val uuid = call.receive<String>()
            feed.decline(uuid)
        }
        post("skip") {
            val uuid = call.receive<String>()
            feed.skip(uuid)
        }
    }
}