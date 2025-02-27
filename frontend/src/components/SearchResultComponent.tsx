import {SearchResult} from "../models/SearchResult";
import * as React from "react";
import {useEffect, useState} from "react";
import axios from "axios";
import {LazyLoadImage} from "react-lazy-load-image-component";
import {addToDownloadList} from "../views/Add";
import {StyledButton} from "./StyledButton";
import {commonStyles} from "../styles/common";

enum SearchResultState {
    UNKNOWN,
    IN_LIBRARY,
    JUST_ADDED,
    ERROR_ADDING
}

export function SearchResultComponent(result: SearchResult) {
    const [existsAlready, setExistsAlready] = useState(SearchResultState.UNKNOWN)
    useEffect(() => {
        const fetchData = async () => {
            const reqRes = await axios.post<boolean>("/api/mediaLibrary/isUrlInLibraryOrProgress", {
                url: result.url
            });
            console.log(reqRes.data);
            if (reqRes.data == true) {
                setExistsAlready(SearchResultState.IN_LIBRARY)
            }
        }
        fetchData()
    }, [])
    return <div key={result.url} className={commonStyles.boxBorder}>
        <a href={`/decrypt?url=${result.url}`} target={"_blank"}>
            <LazyLoadImage
                className={`${commonStyles.cardImage} ${existsAlready == SearchResultState.IN_LIBRARY || existsAlready == SearchResultState.JUST_ADDED ? commonStyles.fadeOut : ""}`}
                src={result.thumbUrl}/>
        </a>
        <a href={`/decrypt?url=${result.url}`} target={"_blank"}>{result.title}</a>
        <StyledButton className={commonStyles.fullWidth}
                      onClick={() => {
                          let prom = addToDownloadList(result.url)
                          prom.then((a) => {
                              if (a.status == 200) {
                                  setExistsAlready(SearchResultState.JUST_ADDED)
                              }
                          })
                      }}
        >
            {existsAlready == SearchResultState.JUST_ADDED ? "Added to queue" : (existsAlready == SearchResultState.IN_LIBRARY ? "Already added" : "Add to queue")}
        </StyledButton>
    </div>;
}
