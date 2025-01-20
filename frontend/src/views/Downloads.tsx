import * as React from "react";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import {StyledButton} from "../components/StyledButton";
import axios from "axios";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import MetadatedDownloadQueueEntry = IoSebi.MetadatedDownloadQueueEntry;
import ProblematicTaskDTO = IoSebi.ProblematicTaskDTO;

function useQueue() {
    const {data, error} = useSWR<Array<MetadatedDownloadQueueEntry>>("/api/queue", fetcher, {refreshInterval: 1000})
    return {
        queue: data,
        isLoading: !error && !data,
        isError: error
    }
}

function useProblematic() {
    const {
        data,
        error
    } = useSWR<Array<ProblematicTaskDTO>>("/api/problematic", fetcher, {refreshInterval: 1000})
    return {
        queue: data,
        isLoading: !error && !data,
        isError: error
    }
}

export function Downloads() {
    const {queue, isLoading, isError} = useQueue()
    const {queue: probQ, isLoading: probLoading, isError: probErr} = useProblematic()
    if (isLoading) return <SectionHeading>Loading Downloads...</SectionHeading>
    return <>
        <MainHeading>Downloads</MainHeading>
        <ul>
            {
                queue?.map(item =>
                    <li key={item.queueEntry.originUrl}>
                        {item.queueEntry.progress != 0 && <progress value={item.queueEntry.progress}/>}
                        <p>
                            {item.title}: {Number(item.queueEntry.progress * 100).toFixed(2)}%
                        </p>
                    </li>
                )
            }
        </ul>
        <SectionHeading>Problematic Entries (<a href={"/api/problematic"}>endpoint</a>)</SectionHeading>
        <ul>
            {
                probQ?.map(item =>
                    <li key={item.originUrl}>
                        <a href={item.originUrl}>
                            {item.originUrl}
                        </a>
                        <p>
                            {item.error}
                        </p>
                        <StyledButton onClick={() => {
                            return axios.post("/api/problematic/remove", {
                                url: item.originUrl
                            })
                        }}>Delete</StyledButton>
                        <StyledButton onClick={() => {
                            return axios.post("/api/problematic/retry", {
                                url: item.originUrl
                            })
                        }}>
                            Retry
                        </StyledButton>
                    </li>
                )
            }
        </ul>
        <div className={commonStyles.fadeOut}/>
    </>;
}
