import * as React from "react";
import {ChangeEvent, useEffect, useState} from "react";
import axios from "axios";
import {useHistory, useParams} from "react-router-dom";
import {MediaLibraryCard} from "../components/MediaLibraryCard";
import {StyledButton} from "../components/StyledButton";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import useSWR from "swr/esm";
import {fetcher} from "../utils";

interface MyPair {
    first: string,
    second: number
}

function usePopTags() {
    const {data, error} = useSWR<Array<MyPair>>(`/api/autotags/popular`, fetcher)
    return {
        popTags: data,
        isLoading: !error && !data,
        isError: error
    }
}

interface SearchQuery {
    query: string | undefined
}

export function MediaLibrary() {
    const {query} = useParams<SearchQuery>();
    const [data, setData] = useState<Array<AutoTaggedMediaLibraryEntry>>([])
    const {popTags} = usePopTags()

    const [searchBarContent, setSearchBarContent] = useState(query ? query : "")
    useEffect(() => {
        const fetchData = async () => {
            const result = await axios.get<Array<AutoTaggedMediaLibraryEntry>>("/api/mediaLibrary?auto");
            console.log(result.data);
            setData(result.data);
        }
        fetchData()
    }, [])

    let handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        setSearchBarContent(e.target.value)
    }

    const history = useHistory();
    let searchTerms = searchBarContent.toLowerCase().split(",").map(s => {
        return s.trim()
    })
    if (searchBarContent.length == 0) {
        searchTerms = []
    }

    console.log(searchTerms)
    let videosFilteredBySearchBar = data.filter(item => {
        return searchTerms.every(term => {
            return item.mediaLibraryEntry.name.toLowerCase().includes(term) || item.autoTags.includes(term)
        })
    })

    if (searchBarContent == "notags") {
        videosFilteredBySearchBar = data.filter(item => {
            return item.autoTags.length == 0
        })
    }

    return (<>
        <h2 className={"text-5xl"}>Media Lib</h2>
        <form onSubmit={(e) => {
            e?.preventDefault()
        }}>
            <label>Filter: </label>
            <input type={"text"} value={searchBarContent} onChange={handleChange}/>
        </form>
        <StyledButton
            className={"inline-block"}
            onClick={() => {
                const item = videosFilteredBySearchBar[Math.floor(Math.random() * videosFilteredBySearchBar.length)];
                history.push(`/movie/${item.mediaLibraryEntry.id}`)
            }}>
            Random video from selection
        </StyledButton>

        <StyledButton
            className={"my-4 mx-3 inline-block"}
            onClick={() => {
                const zeroBois = videosFilteredBySearchBar.filter(item => {
                    return item.mediaLibraryEntry.hits == 0
                })
                const item = zeroBois[Math.floor(Math.random() * zeroBois.length)];
                history.push(`/movie/${item.mediaLibraryEntry.id}`)
            }}>
            Random 0-hit video
        </StyledButton>
        <StyledButton
            className={"my-4 mx-3 inline-block"}
            onClick={() => {
                console.log("sorting!")
                const newthing = [...data].sort((a, b) => (a.mediaLibraryEntry.name > b.mediaLibraryEntry.name) ? 1 : -1)
                console.log(newthing)
                setData(newthing)
            }}>
            Alphabetical Sort
        </StyledButton>

        <StyledButton
            className={"my-4 mx-3 inline-block"}
            onClick={() => {
                console.log("sorting!")
                const newthing = [...data].sort((a, b) => (a.mediaLibraryEntry.hits < b.mediaLibraryEntry.hits) ? 1 : -1)
                console.log(newthing)
                setData(newthing)
            }}>
            Hit Sort
        </StyledButton>
        <StyledButton
            className={"my-4 mx-3 inline-block"}
            onClick={() => {
                const newthing = [...data].sort(() => 0.5 - Math.random())
                setData(newthing)
            }}>
            Shuffle
        </StyledButton>
        <div>
            {popTags?.slice(0, 10)?.map(tag =>
                <StyledButton key={tag.first} className={"inline-block bg-blue-400 mx-1 my-3"} onClick={() => {
                    setSearchBarContent(tag.first)
                }}>
                    {tag.first} <span className={"text-xs"}>({tag.second})</span>
                </StyledButton>
            )
            }

        </div>
        <div className={"grid grid-cols-1 sm:grid-cols-3 gap-8"}>
            {
                videosFilteredBySearchBar
                    .slice(0, 100)
                    .map(item =>
                        <MediaLibraryCard item={item.mediaLibraryEntry} key={item.mediaLibraryEntry.id}/>
                    )
            }
        </div>
    </>);
}