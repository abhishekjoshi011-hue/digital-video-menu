import { useLayoutEffect, useRef } from 'react';

function MenuHeader({ backgroundVideoUrl }) {
  const heroRef = useRef(null);
  const titleRef = useRef(null);

  useLayoutEffect(() => {
    const hero = heroRef.current;
    const title = titleRef.current;

    if (!hero || !title) return;

    const updateTitleBlurWidth = () => {
      const heroRect = hero.getBoundingClientRect();
      const titleRect = title.getBoundingClientRect();
      const leftInset = Math.max(0, titleRect.left - heroRect.left - 18);
      const blurWidth = Math.min(heroRect.width, leftInset + titleRect.width + 42);
      hero.style.setProperty('--title-blur-width', `${Math.round(blurWidth)}px`);
    };

    updateTitleBlurWidth();

    const observer =
      typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver(() => updateTitleBlurWidth())
        : null;

    observer?.observe(hero);
    observer?.observe(title);
    window.addEventListener('resize', updateTitleBlurWidth);

    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', updateTitleBlurWidth);
    };
  }, []);

  return (
    <header className="menu-header">
      <div className="menu-header-media" aria-hidden="true">
        <video
          src={backgroundVideoUrl}
          autoPlay
          muted
          loop
          playsInline
          preload="metadata"
          poster="https://images.pexels.com/photos/262978/pexels-photo-262978.jpeg"
        />
      </div>
      <div className="menu-header-hero" ref={heroRef}>
        <p className="tag">Signature Collection</p>
        <h1 ref={titleRef}>Flavor Table</h1>
        <p className="subtitle">A refined menu experience curated for your table</p>
      </div>
    </header>
  );
}

export default MenuHeader;
