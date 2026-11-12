import { useEffect, useRef } from 'react'
import type { Article } from '../api/articles'
import './ArticleModal.css'

interface ArticleModalProps {
  article: Article | null
  onClose: () => void
}

export default function ArticleModal({ article, onClose }: ArticleModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    if (article && article.fetchStatus === 'SUCCEEDED' && !dialogRef.current?.open) {
      dialogRef.current?.showModal()
    } else if (!article){
      dialogRef.current?.close()
    }
  }, [article])

  return (
    <dialog
      ref={dialogRef}
      className="article-modal"
      aria-labelledby="article-modal-title"
      onClose={onClose}
      onClick={(event) => {
        if (event.target === dialogRef.current) onClose()
      }}
    >
      {article && (
        <div className="article-modal__panel">
          <header className="article-modal__header">
            <h2 id="article-modal-title">{article.title ?? article.url}</h2>
            <button type="button" className="article-modal__close" aria-label="Close" onClick={onClose}>
              ×
            </button>
          </header>
          <div className="article-modal__body">{article.content}</div>
        </div>
      )}
    </dialog>
  )
}
