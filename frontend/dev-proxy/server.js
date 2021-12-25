// https://github.com/terabaud/yesno-clone/blob/master/frontend/dev-proxy/server.js

const {createProxyMiddleware} = require('http-proxy-middleware');
const Bundler = require('parcel-bundler');
const express = require('express');

const bundler = new Bundler('src/index.html');
const app = express();

app.use(
    '/api',
    createProxyMiddleware({
        target: 'http://localhost:8080/',
        onProxyReq: proxyReq => {
            console.log('ProxyReq:', proxyReq.method, proxyReq.path);
        }
    })
);

app.use(bundler.middleware());

app.listen(Number(process.env.PORT || 1234));