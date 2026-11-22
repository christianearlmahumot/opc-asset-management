import { Route, Routes } from 'react-router-dom'
import DocumentsPage from './DocumentsPage'
import ReviewFieldsPage from './ReviewFieldsPage'
import UploadDocumentPage from './UploadDocumentPage'

/** Module 1 routes, mounted at /documents (Administrator, Property Staff, NAS Assistant). */
export default function DocumentRoutes() {
  return (
    <Routes>
      <Route index element={<DocumentsPage />} />
      <Route path="upload" element={<UploadDocumentPage />} />
      <Route path=":id" element={<ReviewFieldsPage />} />
    </Routes>
  )
}
