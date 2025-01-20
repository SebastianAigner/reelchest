import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import * as React from "react";
import {useState} from "react";
import {LazyLoadImage} from "react-lazy-load-image-component";
import axios from "axios";
import {Link} from "react-router-dom";
import {TwoColumnGrid} from "./Layout";
import {commonStyles} from "../styles/common";

export function MediaLibraryCard({item}: { item: MediaLibraryEntry }) {
    const [showingVideo, setShowingVideo] = useState(false)
    let playerOrPicture
    if (!showingVideo) {
        playerOrPicture =
            <LazyLoadImage
                className={commonStyles.cardImage}
                effect={"opacity"}
                src={`/api/mediaLibrary/${item.id}/randomThumb`}
                onClick={() => {
                    setShowingVideo(true)
                    axios.get(`/api/mediaLibrary/${item.id}/hit`)
                }}/>
    } else {
        playerOrPicture = <video
            className={commonStyles.cardImage}
            src={`/api/video/${item.id}`} controls={true}/>
    }
    return <div key={"player-or-picture-card" + item.id}
                className={`${commonStyles.cardContainer} ${item.markedForDeletion ? "border-red-500" : ""}`}
                style={{"height": "fit-content"}}
    >
        {playerOrPicture}
        <TwoColumnGrid>
            <p className={commonStyles.cardTitle}><Link to={`/movie/${item.id}`}>{item.name}</Link></p>
            <p className={commonStyles.cardText}>{item.hits} hits</p>
        </TwoColumnGrid>
    </div>
}
