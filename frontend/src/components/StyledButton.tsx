import * as React from "react";
import {commonStyles} from "../styles/common";

interface StyledButtonProps {
    // onClick: () => void
    children: React.ReactNode
}

export function StyledButton(props: StyledButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>) {
    const disabledClass = props.disabled ? 'opacity-50 cursor-not-allowed hover:bg-green-500 dark:hover:bg-green-600' : '';
    return <button
        {...props}
        className={`block ${commonStyles.greenButton} ${disabledClass} ${props.className || ''}`}
    >
        {props.children}
    </button>
}

/*
<StyledButton disabled = {}/>
 */
