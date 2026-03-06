import React from 'react';

const normalizeDishType = (type, dish = null) => {
  const fallbackRaw =
    type ??
    dish?.type ??
    dish?.dishType ??
    dish?.vegNonVeg ??
    (dish?.isVeg === true ? "veg" : dish?.isVeg === false ? "non-veg" : "");
  const value = String(fallbackRaw || "").toLowerCase().replace(/[^a-z]/g, "");
  if (!value) return "non-veg";
  if (value === "veg" || value === "vegetarian" || value === "vegan" || value.startsWith("veg")) return "veg";
  if (value === "nonveg" || value === "nonvegetarian" || value.includes("nonveg")) return "non-veg";
  return "non-veg";
};

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
        const normalizedType = normalizeDishType(dish.type, dish);
        const videoSrc = dish.videoUrl || dish.video;
        const imageSrc = dish.image || dish.imageUrl;
        const hasMedia = Boolean(videoSrc || imageSrc);

        return (
          <div key={dish.id || dish.name} className={`dish-card ${hasMedia ? 'has-media' : ''}`}>
            {hasMedia && (
              <div className="dish-card-bg" aria-hidden="true">
                {videoSrc ? (
                  <video
                    src={videoSrc}
                    className="dish-card-bg-media"
                    autoPlay
                    muted
                    loop
                    playsInline
                    preload="metadata"
                    poster={imageSrc || undefined}
                  />
                ) : (
                  <img
                    src={imageSrc}
                    alt=""
                    className="dish-card-bg-media"
                  />
                )}
              </div>
            )}

            <div className="dish-card-content">
            <div className="dish-meta">
              <span
                className={`type-pill ${normalizedType || "non-veg"}`}
                aria-label={normalizedType === 'veg' ? 'Vegetarian dish' : 'Non-vegetarian dish'}
                title={normalizedType === 'veg' ? 'Veg' : 'Non-Veg'}
              >
                <span className="type-marker" aria-hidden="true" />
              </span>
              {dish.category && <span className="category-pill">{dish.category}</span>}
            </div>

              <div className="dish-copy">
                <h3>
                  <button
                    type="button"
                    className="dish-name-btn"
                    onClick={() => onOpenDishDetails?.(dish)}
                  >
                    {dish.name}
                  </button>
                </h3>
                <p>{dish.description || "Freshly prepared and served hot."}</p>
              </div>

              <div className="dish-footer">
                <strong>${Number(dish.price || 0).toFixed(2)}</strong>
                <button onClick={() => onAddToCart(dish)} className="add-btn">
                  Add to Cart
                </button>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default MenuList;
