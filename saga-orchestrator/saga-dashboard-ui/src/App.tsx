import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { SagaListPage } from './pages/SagaListPage';
import { SagaDetailPage } from './pages/SagaDetailPage';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-gray-50">
        <nav className="bg-white border-b border-gray-200 shadow-sm">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex items-center justify-between h-14">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center">
                  <span className="text-white font-bold text-sm">SO</span>
                </div>
                <span className="font-semibold text-gray-900">Saga Orchestrator</span>
              </div>
            </div>
          </div>
        </nav>

        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Routes>
            <Route path="/" element={<SagaListPage />} />
            <Route path="/sagas/:sagaId" element={<SagaDetailPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
