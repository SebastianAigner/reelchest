import * as React from "react";
import {ReactNode} from "react";
import {commonStyles} from "../styles/common";

interface TagBadgeProps {
    children: ReactNode;
    onClick?: () => void;
}

export function TagBadge({children, onClick}: TagBadgeProps) {
    return (
        <p className={`${commonStyles.greenButton} ${commonStyles.inlineBlock}`} onClick={onClick}>
            {children}
        </p>
    );
}
