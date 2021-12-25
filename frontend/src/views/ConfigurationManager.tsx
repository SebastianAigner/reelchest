import * as React from "react";
import {useEffect, useState} from "react";
import axios from "axios";

interface ConfigurationProps {
    endpoint: string
}

export function ConfigurationManager(props: ConfigurationProps) {
    const [ruleSet, setRuleSet] = useState("")
    useEffect(() => {
        const fetchData = async () => {
            const result = await axios.get<string>(`/api/${props.endpoint}`);
            console.log(result.data);
            setRuleSet(result.data);
        }
        fetchData()
    }, [props.endpoint])

    return <>
        <h2 className={"text-5xl my-5"}>Configuration: {props.endpoint}</h2>
        <p/>
        <textarea className={"w-full h-56 font-mono"} value={ruleSet} onChange={(e) => {
            setRuleSet(e.target.value)
        }
        }/>
        <button onClick={() => {
            axios.post<void>(`/api/${props.endpoint}`, ruleSet)
        }
        }>submit
        </button>
    </>
}