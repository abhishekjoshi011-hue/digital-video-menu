function MenuHeader({ backgroundVideoUrl, backgroundImageUrl, title = 'Digital Menu' }) {
  return (
    <header className="menu-header">
      <div className="menu-header-split">
        <div className="menu-header-media" aria-hidden="true">
          {backgroundVideoUrl ? (
            <video
              src={backgroundVideoUrl}
              autoPlay
              muted
              loop
              playsInline
              preload="metadata"
              poster={backgroundImageUrl || undefined}
            />
          ) : backgroundImageUrl ? (
            <img src={backgroundImageUrl} alt="" />
          ) : (
            <div className="menu-header-media-empty" />
          )}
        </div>
        <div className="menu-header-brand-pane">
          <p className="menu-brand-kicker">Welcome To</p>
          <div className="menu-title-strip">
            <h1>{title}</h1>
          </div>
        </div>
      </div>
    </header>
  );
}

export default MenuHeader;
