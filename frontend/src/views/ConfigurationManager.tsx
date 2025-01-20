import * as React from "react";
import {useEffect, useState} from "react";
import axios from "axios";
import {MainHeading} from "../components/Typography";

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
        <div className="my-5"><MainHeading>Configuration: {props.endpoint}</MainHeading></div>
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
