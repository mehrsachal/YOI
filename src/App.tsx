import { useState, useEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/Layout/Navbar';
import Sidebar from './components/Layout/Sidebar';
import Dashboard from './pages/Dashboard';
import TheoryPage from './pages/TheoryPage';
import WeaponPage from './pages/WeaponPage';
import QuizEngine from './pages/QuizEngine';
import Flashcards from './pages/Flashcards';
import './App.css';

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  // Close sidebar on navigation (for mobile)
  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  return (
    <div className="app-layout">
      <Navbar onToggleSidebar={() => setSidebarOpen(!sidebarOpen)} />
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      
      <main className="main-content">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/theory" element={<TheoryPage />} />
          <Route path="/weapons/:weaponId" element={<WeaponPage />} />
          <Route path="/quiz" element={<QuizEngine />} />
          <Route path="/flashcards" element={<Flashcards />} />
          <Route path="*" element={
            <div className="page-container">
              <div className="page-header">
                <h1>Under Construction</h1>
                <p>This module is currently being built.</p>
              </div>
            </div>
          } />
        </Routes>
      </main>
    </div>
  );
}
