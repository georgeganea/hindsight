import React from 'react/addons';

/*
 * @class Item
 * @extends React.Component
 */
class Item extends React.Component {

  /*
   * @method shouldComponentUpdate
   * @returns {Boolean}
   */
  shouldComponentUpdate () {
    return React.addons.PureRenderMixin.shouldComponentUpdate.apply(this, arguments);
  }

  handleClick (event) {
    // console.log("-->"+ event.target.id);
    var commits = event.target.id.split("-");
    console.log("-->"+ commits);
    if (commits[0] && commits[1])
      theJavaFunction("OpenCommitDiff", commits[0], commits[1],  this.props.item.filePath)
  }

  /*
  {this.props.item.commitID}
   * @method render
   * @returns {JSX}
   */
  render () {
    //  {this.props.item.accesses.map(function (item)
    var cells = [];
    for (var i=0; i < this.props.item.rels.length; i++) {
      if (this.props.item.rels[i])
        cells.push(<div className="accesses" id={this.props.item.rels[i]} onClick={this.handleClick.bind(this)}></div>);
      else
        cells.push(<div className="accesseswhite" id={this.props.item.rels[i]} onClick={this.handleClick.bind(this)}></div>);
    }
    // return <div className="versions" onClick={this.handleClick.bind(this)}>
    //          {rows}
    //        </div>
    return <div className='item'>{cells}<div class='line'>{this.props.item.entityId}</div></div>
  }
}

// Prop types validation
Item.propTypes = {
  item: React.PropTypes.object.isRequired,
};

export default Item;
