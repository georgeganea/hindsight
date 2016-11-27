import React from 'react/addons';
import Item from './Item';

/*
 * @class Cart
 * @extends React.Component
 */
class Cart extends React.Component {



  constructor () {
    super();
    this.messageReceived = this.messageReceived.bind(this);
    document.addEventListener('build', this.messageReceived);
    this.state = {'msg':'nothing', 'history':[], 'names':[]}
    if (typeof theJavaFunction === 'function')
      this.dbstatus = theJavaFunction("GetDBStatus");
  }



  messageReceived (e) {
      console.log(e.detail);
      console.log(this.props);
      this.setState(e.detail);
      console.log(this.props);
      // theJavaFunction(12, false, null, [3.6, ['swt', true]], 'eclipse');
  }

  // /*
  //  * @method shouldComponentUpdate
  //  * @returns {Boolean}
  //  */
  // shouldComponentUpdate () {
  //   return React.addons.PureRenderMixin.shouldComponentUpdate.apply(this, arguments);
  // }

  /*
   * @method render
   * @returns {JSX}
   */
  render () {
    return <div className="cart">
              {this.state.history.map(function (item, key) {
                    return <Item item={item} />;
                  })
              }
          </div>
    }
}
    // return <div>
    //         <p>db status is {this.dbstatus}</p>
    //         <div className="cart">
    //           {this.state.history.map(function (item, key) {
    //             return <Item key={key} item={item} accNo={accNumber}/>;
    //           })}
    //           <div className="attrnames">
    //           {this.state.names.map(function (name, key) {
    //             return <div className="attrname">{name}</div>;
    //           })}
    //           </div>
    //         </div>
    //       </div>




// Prop types validation
Cart.propTypes = {
  cart: React.PropTypes.object.isRequired,
};

export default Cart;
