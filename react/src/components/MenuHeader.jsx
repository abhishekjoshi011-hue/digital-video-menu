function MenuHeader({ backgroundVideoUrl }) {
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
      <div className="menu-title-strip">
        <h1>Soy Affair</h1>
      </div>
    </header>
  );
}

export default MenuHeader;
