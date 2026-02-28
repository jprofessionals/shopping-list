import { createRoot, type Root } from 'react-dom/client';
import { ShoppingListWidgetApp } from './ShoppingListWidget';

class ShoppingListWidgetElement extends HTMLElement {
  private root: Root | null = null;
  private shadow: ShadowRoot;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: 'open' });
  }

  static get observedAttributes() {
    return ['token', 'api-url', 'theme'];
  }

  connectedCallback() {
    this.render();
  }

  disconnectedCallback() {
    this.root?.unmount();
    this.root = null;
  }

  attributeChangedCallback() {
    this.render();
  }

  private render() {
    const token = this.getAttribute('token');
    const apiUrl = this.getAttribute('api-url');
    const theme = (this.getAttribute('theme') as 'light' | 'dark') || 'light';

    if (!token || !apiUrl) {
      this.shadow.innerHTML = '<p>Missing required attributes: token, api-url</p>';
      return;
    }

    const container =
      this.shadow.querySelector('#widget-root') ||
      (() => {
        const div = document.createElement('div');
        div.id = 'widget-root';
        this.shadow.appendChild(div);
        return div;
      })();

    if (!this.root) {
      this.root = createRoot(container);
    }

    this.root.render(<ShoppingListWidgetApp token={token} apiUrl={apiUrl} theme={theme} />);
  }
}

customElements.define('shopping-list-widget', ShoppingListWidgetElement);
