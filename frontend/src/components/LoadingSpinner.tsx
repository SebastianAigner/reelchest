import React from 'react';
import {commonStyles} from "../styles/common";

const spinnerStyles = {
    spinner: {
        width: '40px',
        height: '40px',
        margin: '20px auto',
        border: '4px solid #f3f3f3',
        borderTop: '4px solid #3498db',
        borderRadius: '50%',
        animation: 'spin 1s linear infinite',
    },
    container: {
        textAlign: 'center' as const,
        padding: '20px',
        color: '#666',
    },
    '@keyframes spin': {
        '0%': {transform: 'rotate(0deg)'},
        '100%': {transform: 'rotate(360deg)'},
    },
};

export function LoadingSpinner() {
    return (
        <div style={spinnerStyles.container}>
            <style>
                {`
                @keyframes spin {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(360deg); }
                }
                `}
            </style>
            <div style={spinnerStyles.spinner}/>
            <div className={commonStyles.verticalMargin}>Loading results...</div>
        </div>
    );
}