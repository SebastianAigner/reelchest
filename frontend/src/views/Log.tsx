import * as React from "react";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import LogEntry = IoSebi.LogEntry;

function useLog() {
    const {data, error} = useSWR<Array<LogEntry>>(`/api/log`, fetcher, {refreshInterval: 1000})
    return {
        log: data,
        isLoading: !error && !data,
        isError: error
    }
}


export function Log() {
    const {log, isLoading, isError} = useLog()
    return <>
        <h2 className={"text-5xl"}>Logs</h2>
        <ul className={"font-mono"}>
            {
                log?.map(item =>
                    <li key={item.formattedMessage}>
                        <p>{item.formattedMessage}</p>
                    </li>
                )
            }
        </ul>
        <div className={"opacity-25"}/>
    </>;
}