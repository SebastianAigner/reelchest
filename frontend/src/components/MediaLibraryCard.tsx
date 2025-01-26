import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import * as React from "react";
import {useState} from "react";
import {LazyLoadImage} from "react-lazy-load-image-component";
import axios from "axios";
import {Link} from "react-router-dom";
import {TwoColumnGrid} from "./Layout";
import {commonStyles} from "../styles/common";
import placeholderImage from '../assets/placeholder.svg';
import audioPlaceholder from '../assets/audio-placeholder.svg';
import {useMediaInfo} from "../hooks/useMediaInfo";
import {TrashIcon} from '@heroicons/react/24/solid';
import {useInView} from "react-intersection-observer";

export function MediaLibraryCard({item}: { item: MediaLibraryEntry }) {
    const [showingVideo, setShowingVideo] = useState(false)
    const {ref, inView} = useInView({threshold: 0.1});
    const {isAudio} = useMediaInfo(item.id, inView)
    let playerOrPicture
    if (!showingVideo) {
        playerOrPicture =
            <LazyLoadImage
                className={commonStyles.cardImage}
                effect={"opacity"}
                src={isAudio ? audioPlaceholder : `/api/mediaLibrary/${item.id}/randomThumb`}
                onError={(e: any) => {
                    e.target.src = placeholderImage;
                }}
                onClick={() => {
                    setShowingVideo(true)
                    axios.get(`/api/mediaLibrary/${item.id}/hit`)
                }}/>
    } else {
        playerOrPicture = isAudio ? (
            <audio
                className={commonStyles.cardImage}
                style={{background: '#2C2C2C'}}
                src={`/api/video/${item.id}`}
                controls={true}/>
        ) : (
            <video
                className={commonStyles.cardImage}
                src={`/api/video/${item.id}`}
                controls={true}/>
        )
    }
    return <div
        ref={ref}
        key={"player-or-picture-card" + item.id}
                className={`${commonStyles.cardContainer} ${
                    item.markedForDeletion
                        ? "border-2 border-red-400 bg-red-50 dark:bg-red-900/10 relative opacity-75"
                        : ""
                }`}
                style={{"height": "fit-content"}}
    >
        {item.markedForDeletion && (
            <div className="absolute top-2 right-2 bg-red-500/90 text-white p-1.5 rounded-full shadow-sm">
                <TrashIcon className="w-4 h-4"/>
            </div>
        )}
        {playerOrPicture}
        <TwoColumnGrid>
            <p className={commonStyles.cardTitle}><Link to={`/movie/${item.id}`}>{item.name}</Link></p>
            <p className={commonStyles.cardText}>{item.hits} hits</p>
        </TwoColumnGrid>
    </div>
}
