import React from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from './App';
import { ProgressProvider } from './context/ProgressContext';
import './index.css';

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <React.StrictMode>
      <HashRouter>
        <ProgressProvider>
          <App />
        </ProgressProvider>
      </HashRouter>
    </React.StrictMode>
  );
}
