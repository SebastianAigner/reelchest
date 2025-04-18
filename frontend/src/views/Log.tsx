import * as React from "react";
import useSWR from "swr";
import {fetcher} from "../utils";
import {MainHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
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
        <MainHeading>Logs</MainHeading>
        <ul className={commonStyles.monospace}>
            {
                log?.map(item =>
                    <li key={item.formattedMessage}>
                        <p>{item.formattedMessage}</p>
                    </li>
                )
            }
        </ul>
        <div className={commonStyles.fadeOut}/>
    </>;
}
