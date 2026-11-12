import '@testing-library/jest-dom'

// jsdom doesn't implement HTMLDialogElement.showModal/close.
HTMLDialogElement.prototype.showModal = function showModal() {
  this.setAttribute('open', '')
}
HTMLDialogElement.prototype.close = function close() {
  this.removeAttribute('open')
  this.dispatchEvent(new Event('close'))
}
