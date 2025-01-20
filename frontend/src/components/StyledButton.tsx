import * as React from "react";
import {commonStyles} from "../styles/common";

interface StyledButtonProps {
    // onClick: () => void
    children: React.ReactNode
}

export function StyledButton(props: StyledButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>) {
    return <button
        {...props}
        className={`block ${commonStyles.greenButton} ${props.className}`}
    >
        {props.children}
    </button>
}

/*
<StyledButton disabled = {}/>
 */
