import React from "react";

import "./page-container.css";

export default function PageContainer({ children }) {
  // Шапка теперь показывается глобально в App.jsx
  // PageContainer только оборачивает контент
  // Обработчик свайпа добавлен глобально в App.jsx через SwipeBackHandler
  return (
    <div className="page-content">
      {children}
    </div>
  );
}
