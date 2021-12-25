import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import * as React from "react";
import {useState} from "react";
import {LazyLoadImage} from "react-lazy-load-image-component";
import axios from "axios";
import {Link} from "react-router-dom";

export function MediaLibraryCard({item}: { item: MediaLibraryEntry }) {
    const [showingVideo, setShowingVideo] = useState(false)
    let playerOrPicture
    if (!showingVideo) {
        playerOrPicture =
            <LazyLoadImage
                className={`object-cover rounded-md h-48 w-full`}
                effect={"opacity"}
                src={`/api/mediaLibrary/${item.id}/randomThumb`}
                onClick={() => {
                    setShowingVideo(true)
                    axios.get(`/api/mediaLibrary/${item.id}/hit`)
                }}/>
    } else {
        playerOrPicture = <video
            className={`object-fit rounded-md h-48 w-full`}
            src={`/api/video/${item.id}`} controls={true}/>
    }
    return <div key={"player-or-picture-card" + item.id}
                className={"border-2 rounded-xl  p-3 shadow"}
                style={{"height": "fit-content"}}
    >
        {playerOrPicture}
        <div className={"grid grid-cols-2"}>
            <p className={"py-3 font-semibold"}><Link to={`/movie/${item.id}`}>{item.name}</Link></p>
            <p className={"py-3 text-right"}>{item.hits} hits</p>
        </div>
    </div>
}