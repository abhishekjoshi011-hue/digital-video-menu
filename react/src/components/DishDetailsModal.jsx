import { useEffect, useState } from 'react';
import DishMediaCarousel from './DishMediaCarousel';

const extractAddOns = (dish) => {
  const raw = dish?.addOns ?? dish?.addons ?? dish?.addOnOptions ?? dish?.addonOptions;
  if (!raw) return [];

  if (Array.isArray(raw)) {
    return raw.map((item) => (typeof item === 'string' ? { name: item } : item)).filter((item) => item?.name);
  }

  if (typeof raw === 'string') {
    return raw.split(',').map((item) => item.trim()).filter(Boolean).map((item) => ({ name: item }));
  }

  return [];
};

function DishDetailsModal({ dish, onClose }) {
  const [selectedAddOns, setSelectedAddOns] = useState([]);

  useEffect(() => {
    setSelectedAddOns([]);
  }, [dish?.id, dish?.name]);

  if (!dish) return null;

  const addOns = extractAddOns(dish);

  const toggleAddon = (name) => {
    setSelectedAddOns((prev) =>
      prev.includes(name) ? prev.filter((item) => item !== name) : [...prev, name]
    );
  };

  return (
    <div className="dish-modal-backdrop" role="presentation" onClick={onClose}>
      <div className="dish-modal" role="dialog" aria-modal="true" aria-label={`${dish.name} details`} onClick={(event) => event.stopPropagation()}>
        <button type="button" className="dish-modal-close" onClick={onClose} aria-label="Close details">
          ×
        </button>

        <h2>{dish.name}</h2>

        <div className="dish-modal-video">
          <DishMediaCarousel
            dish={dish}
            showDots
            showArrows
            emptyLabel="No media available for this dish."
          />
        </div>

        <div className="dish-modal-section">
          <h3>Description</h3>
          <p>{dish.description || 'No description available.'}</p>
        </div>

        <div className="dish-modal-section">
          <h3>Add-ons</h3>
          {addOns.length === 0 ? (
            <p className="dish-addons-empty">No add-ons listed for this dish yet.</p>
          ) : (
            <ul className="dish-addons-list">
              {addOns.map((addon) => {
                const label = addon.price != null ? `${addon.name} (+$${Number(addon.price).toFixed(2)})` : addon.name;
                return (
                  <li key={addon.name}>
                    <label>
                      <input
                        type="checkbox"
                        checked={selectedAddOns.includes(addon.name)}
                        onChange={() => toggleAddon(addon.name)}
                      />
                      <span>{label}</span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

export default DishDetailsModal;
