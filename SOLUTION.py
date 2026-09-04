from android.graphics import Bitmap
from android.media import MediaMetadata

class ConflatedEventBus:
    def set_metadata(self, metadata, event):
        """Event bus that properly handles bitmap metadata to avoid recycled source issues."""
        if event is not None and event.get_picture() is not None:
            picture = event.get_picture()
            
            if picture is not None and not picture.is_recycled():
                # Create a fresh copy to avoid recycled source issues in createScaledBitmap
                scaled = picture.create_scaled_bitmap(
                    picture.get_width(),
                    picture.get_height()
                )
                metadata.set_picture(scaled)
                return metadata
        
        return metadata


class MediaSession:
    def __init__(self):
        self._session = None
        
    def _get_metadata(self):
        """Get current metadata builder ensuring proper bitmap handling."""
        if self._session is not None:
            return self._session.get_metadata()
        return MediaMetadata.Builder()
    
    def set_metadata(self, builder):
        """Set metadata with safe bitmap handling."""
        if builder is not None:
            # Ensure we're not passing a recycled source
            picture = builder.get_picture()
            
            if picture is not None:
                # Force a fresh copy by creating a scaled version matching original dimensions
                fresh_bitmap = picture.create_scaled_bitmap(
                    picture.get_width(),
                    picture.get_height()
                )
                builder.set_picture(fresh_bitmap)
                
            self._session = builder
        return builder
    
    def get_picture(self):
        """Safely get picture, handling potential null states."""
        if self._session is not None:
            pic = self._session.get_picture()
            if pic is not None and pic.is_recycled():
                return pic.create_scaled_bitmap(pic.get_width(), pic.get_height())
            return pic
        return None


class VideoService:
    def __init__(self):
        self._event_bus = ConflatedEventBus()
        self._media_session = MediaSession()
        
    def on_video_started(self, event):
        """Handle video playback events with fixed bitmap handling."""
        metadata = self._event_bus.set_metadata(
            self._media_session.get_metadata(),
            event
        )
        
        # Rebuild metadata to ensure clean state
        if metadata is not None:
            self._media_session = self._media_session.set_metadata(
                MediaMetadata.Builder.build(metadata.build())
            )
            
        return metadata
    
    def _create_metadata(self, event):
        """Create fresh metadata for event with safe bitmap scaling."""
        builder = MediaMetadata.Builder()
        picture = event.get_picture()
        
        if picture is not None:
            # Ensure the picture is fresh before scaling
            scaled = picture.create_scaled_bitmap(
                picture.get_width(),
                picture.get_height()
            )
            builder.set_picture(scaled)
            
        return builder
    
    def on_video_updated(self, event):
        """Handle video metadata updates to prevent crashes."""
        if event is not None:
            picture = event.get_picture()
            
            if picture is not None:
                # Create a new scaled bitmap to prevent recycled source issues
                scaled_bitmap = picture.create_scaled_bitmap(
                    picture.get_width(),
                    picture.get_height()
                )
                
                # Build metadata with the fresh scaled bitmap
                metadata = MediaMetadata.Builder()
                metadata.set_picture(scaled_bitmap)
                metadata.set_media_title(event.get_title())
                
                # Set metadata through the conflated event bus
                self._event_bus = self._event_bus.set_metadata(
                    metadata,
                    event
                )
                
        return self._event_bus
    
    def set_video_thumbnail(self, event):
        """Set video thumbnail using safe bitmap logic."""
        if event is not None:
            thumbnail = event.get_thumbnail()
            
            if thumbnail is not None:
                # Handle recycled source by creating a fresh instance
                fresh_thumbnail = thumbnail.create_scaled_bitmap(
                    thumbnail.get_width(),
                    thumbnail.get_height()
                )
                
                metadata = MediaMetadata.Builder()
                metadata.set_picture(fresh_thumbnail)
                
                self._event_bus.set_metadata(metadata, event)
                
        return self._event_bus