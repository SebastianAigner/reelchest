import * as React from "react";
import {useEffect, useState} from "react";
import axios from "axios";
import {MainHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";

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
        <MainHeading>Configuration: {props.endpoint}</MainHeading>
        <p/>
        <textarea className={`${commonStyles.fullWidth} ${commonStyles.textareaHeight} ${commonStyles.monospace}`}
                  value={ruleSet} onChange={(e) => {
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
