import Debug from 'debug';
import App from '../../app';

var attachElement = document.getElementById('app');

var state = {
      cart: {
          msg: 'My Cart',
          history: [
            {
              commitID: 'itemunu'
            },
            {
              commitID: 'Item 2'
            }
          ]
        }
};

var app;

Debug.enable('myApp*');

// Create new app and attach to element
app = new App({
  state: state
});

var renderedApp = app.renderToDOM(attachElement);
