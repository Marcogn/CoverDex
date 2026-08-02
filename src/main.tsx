import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';
import { initServiceWorker } from './utils/registerServiceWorker';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

void initServiceWorker();
