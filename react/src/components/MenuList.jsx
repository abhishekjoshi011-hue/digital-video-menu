import React from 'react';
import DishMediaCarousel from './DishMediaCarousel';

const MenuList = ({ items, onAddToCart, onOpenDishDetails }) => {
  if (!items || items.length === 0) {
    return (
      <div className="no-items" style={{ textAlign: 'center', padding: '20px' }}>
        <h3>No dishes found.</h3>
      </div>
    );
  }

  return (
    <div className="menu-list">
      {items.map((dish) => {
        return (
          <div key={dish.id || dish.name} className="dish-card dish-card-simple dish-card-split">
            <div className="dish-split-left">
              <div className="dish-media-top">
                <DishMediaCarousel
                  dish={dish}
                  onClick={() => onOpenDishDetails?.(dish)}
                  emptyLabel="No image"
                />
              </div>
              <strong className="dish-split-price">${Number(dish.price || 0).toFixed(2)}</strong>
            </div>
            <div className="dish-split-right">
              <button
                type="button"
                className="dish-name-btn"
                onClick={() => onOpenDishDetails?.(dish)}
              >
                {dish.name}
              </button>
              <small className="dish-split-meta">{dish.category || 'Signature'}</small>
              <button onClick={() => onAddToCart(dish)} className="add-btn">
                Add to Cart
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default MenuList;
