import React, {createContext, ReactNode, useContext, useState} from 'react';
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";

interface PlaylistContextType {
    playlist: AutoTaggedMediaLibraryEntry[];
    currentIndex: number;
    addToPlaylist: (entries: AutoTaggedMediaLibraryEntry[]) => void;
    clearPlaylist: () => void;
    getNextVideo: () => AutoTaggedMediaLibraryEntry | null;
    getCurrentVideo: () => AutoTaggedMediaLibraryEntry | null;
    peekNextVideo: () => AutoTaggedMediaLibraryEntry | null;
}

const PlaylistContext = createContext<PlaylistContextType | undefined>(undefined);

export function PlaylistProvider({children}: { children: ReactNode }) {
    const [playlist, setPlaylist] = useState<AutoTaggedMediaLibraryEntry[]>([]);
    const [currentIndex, setCurrentIndex] = useState<number>(0);

    const addToPlaylist = (entries: AutoTaggedMediaLibraryEntry[]) => {
        setPlaylist(entries);
        setCurrentIndex(0);
    };

    const clearPlaylist = () => {
        setPlaylist([]);
        setCurrentIndex(0);
    };

    const peekNextVideo = () => {
        if (playlist.length === 0) return null;
        const nextIndex = (currentIndex + 1) % playlist.length;
        return playlist[nextIndex];
    };

    const getNextVideo = () => {
        if (playlist.length === 0) return null;
        const nextIndex = (currentIndex + 1) % playlist.length;
        setCurrentIndex(nextIndex);
        return playlist[nextIndex];
    };

    const getCurrentVideo = () => {
        if (playlist.length === 0) return null;
        return playlist[currentIndex];
    };

    return (
        <PlaylistContext.Provider value={{
            playlist,
            currentIndex,
            addToPlaylist,
            clearPlaylist,
            getNextVideo,
            getCurrentVideo,
            peekNextVideo,
        }}>
            {children}
        </PlaylistContext.Provider>
    );
}

export function usePlaylist() {
    const context = useContext(PlaylistContext);
    if (context === undefined) {
        throw new Error('usePlaylist must be used within a PlaylistProvider');
    }
    return context;
}
