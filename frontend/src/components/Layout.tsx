import * as React from "react";
import {ReactNode} from "react";
import {commonStyles} from "../styles/common";

interface LayoutProps {
    children: ReactNode;
    className?: string;
}

export function HStack({children, className = ""}: LayoutProps) {
    return (
        <div className={`${commonStyles.flexRow} ${className}`}>
            {children}
        </div>
    );
}

export function ScrollableHStack({children, className = ""}: LayoutProps) {
    return (
        <div className={`${commonStyles.flexRowScroll} ${className}`}>
            {children}
        </div>
    );
}

export function VStack({children, className = ""}: LayoutProps) {
    return (
        <div className={`${commonStyles.flexCol} ${className}`}>
            {children}
        </div>
    );
}

export function FlexGrow({children, className = ""}: LayoutProps) {
    return (
        <div className={`${commonStyles.flexGrow} ${className}`}>
            {children}
        </div>
    );
}

export function ResponsiveGrid({children, className = ""}: LayoutProps) {
    return (
        <div className={`${commonStyles.responsiveGrid} ${className}`}>
            {children}
        </div>
    );
}

export function TwoColumnGrid({children, className = ""}: LayoutProps) {
    return (
        <div className={`${commonStyles.twoColumnGrid} ${className}`}>
            {children}
        </div>
    );
}

export function NavGrid({children, className = ""}: LayoutProps) {
    return (
        <nav className={`${commonStyles.navGrid} ${className}`}>
            {children}
        </nav>
    );
}
