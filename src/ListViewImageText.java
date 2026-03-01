package com.bosonshiggs.listviewimagetext;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.text.HtmlCompat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.HVArrangement;
import com.google.appinventor.components.runtime.util.YailList;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@DesignerComponent(version = 24, versionName = "23.2",
    description = "Music playlist ListView with 1‑based indices, play/pause indicator, ID‑based duplicate prevention, corner radius fix, programmatic control, customizable row height, scroll animation, individual button colors, refresh, fixed scrolling/position tags, bottom‑reached event, clear‑and‑replace, select with playing state, and JSON liked‑by helpers. Call AnchorTo() to place it.",
    iconName = "icon.png")
public class ListViewImageText extends AndroidNonvisibleComponent
    implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, AbsListView.OnScrollListener {

  private final Context context;
  private final ListView listView;
  private final Adapter adapter;

  // Instance data – no static sharing
  private final List<Item> items = new ArrayList<>();
  private final List<Item> originalItems = new ArrayList<>();
  private final Map<String, Drawable> imageCache = new HashMap<>();
  private final ExecutorService executor = Executors.newFixedThreadPool(4);
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  // Key for storing position in view tags
  private static final int KEY_POSITION = 123456789;

  // Current user for like detection
  private String currentUser = "";

  // Material Icons font
  private Typeface materialIconsTypeface;

  // Selection (0‑based internally)
  private int selectedPosition = -1;
  private int highlightColor = 0xFFE0E0E0;

  // Appearance
  private int bgColor = Color.TRANSPARENT;
  private int dividerColor = Color.parseColor("#80FFFFFF");
  private int dividerHeight = 1;
  private int imageSide = 1; // 1=left, 2=right

  // Row height (0 = wrap content)
  private int rowHeight = 0;

  // Image dimensions
  private int imageWidth = 64;
  private int imageHeight = 64;
  private int cornerRadius = 0;

  // Title (track name)
  private String titleFontTypeface = Component.TYPEFACE_DEFAULT;
  private boolean titleFontBold;
  private boolean titleFontItalic;
  private float titleTextSize = 16f;
  private int titleColor = Color.BLACK;
  private boolean titleHtml;

  // Artist (subtitle)
  private String artistFontTypeface = Component.TYPEFACE_DEFAULT;
  private boolean artistFontBold;
  private boolean artistFontItalic;
  private float artistTextSize = 14f;
  private int artistColor = 0xFF444444;
  private boolean artistHtml;

  // Duration
  private String durationFontTypeface = Component.TYPEFACE_DEFAULT;
  private boolean durationFontBold;
  private boolean durationFontItalic;
  private float durationTextSize = 12f;
  private int durationColor = 0xFF888888;

  // Button properties – individual colors
  private float buttonTextSize = 18f;
  private int downloadButtonColor = Color.BLACK;
  private int likeButtonColor = Color.BLACK;
  private int likedButtonColor = Color.RED;
  private int playButtonColor = Color.BLACK;

  private String downloadButtonText = "\uE2C4"; // cloud download
  private String likeButtonText = "\uE87E";      // favorite border
  private String likedButtonText = "\uE87D";     // favorite filled
  private String playIconText = "\uE037";        // play arrow
  private String pauseIconText = "\uE034";       // pause

  // Scroll animation flag
  private boolean scrollAnimationEnabled = true;

  // Fallback images
  private String placeholderImageUrl = "https://cdn-icons-png.flaticon.com/512/7500/7500224.png";
  private String errorImageUrl = "https://cdn-icons-png.flaticon.com/512/2748/2748558.png";
  private String genericImageUrl = "https://cdn-icons-png.flaticon.com/512/7500/7500224.png";

  // Swipe thresholds
  private static final int SWIPE_THRESHOLD = 100;
  private static final int SWIPE_VELOCITY_THRESHOLD = 100;

  // For scroll direction detection
  private int lastFirstVisibleItem = -1;

  // Bottom detection
  private boolean bottomReachedEventFired = false;

  public ListViewImageText(ComponentContainer container) {
    super(container.$form());
    context = container.$context();
    listView = new ListView(context);
    listView.setBackgroundColor(bgColor);
    listView.setDivider(new ColorDrawable(dividerColor));
    listView.setDividerHeight(dividerHeight);
    listView.setOnItemClickListener(this);
    listView.setOnItemLongClickListener(this);
    listView.setOnScrollListener(this);

    adapter = new Adapter();
    listView.setAdapter(adapter);

    try {
      materialIconsTypeface = Typeface.createFromAsset(context.getAssets(), "MaterialIcons-Regular.ttf");
    } catch (Exception e) {
      materialIconsTypeface = Typeface.DEFAULT;
    }
  }

  // ==================== EVENTS (all 1‑based, include ID) ====================

  @SimpleEvent(description = "Fired when an item is clicked. Position is 1‑based.")
  public void ItemSelected(int position, String id, String title, String artist, String image, String duration, boolean liked) {
    EventDispatcher.dispatchEvent(this, "ItemSelected", position, id, title, artist, image, duration, liked);
  }

  @SimpleEvent(description = "Fired when an item is long-clicked. Position is 1‑based.")
  public void ItemLongClick(int position, String id, String title, String artist, String image, String duration, boolean liked) {
    EventDispatcher.dispatchEvent(this, "ItemLongClick", position, id, title, artist, image, duration, liked);
  }

  @SimpleEvent(description = "Fired when the like button is clicked. Position is 1‑based.")
  public void LikeClicked(int position, String id, String title, String artist, String image, String duration, boolean liked) {
    EventDispatcher.dispatchEvent(this, "LikeClicked", position, id, title, artist, image, duration, liked);
  }

  @SimpleEvent(description = "Fired when the download button is clicked. Position is 1‑based.")
  public void DownloadClicked(int position, String id, String title, String artist, String image, String duration) {
    EventDispatcher.dispatchEvent(this, "DownloadClicked", position, id, title, artist, image, duration);
  }

  @SimpleEvent(description = "Fired when the play/pause button is clicked. Position is 1‑based.")
  public void PlayPauseClicked(int position, String id, String title, String artist, String image, String duration, boolean isPlaying) {
    EventDispatcher.dispatchEvent(this, "PlayPauseClicked", position, id, title, artist, image, duration, isPlaying);
  }

  @SimpleEvent(description = "Fired when the user swipes an item from right to left. Position is 1‑based.")
  public void ItemSwipedLeft(int position, String id, String title, String artist, String image, String duration, boolean liked) {
    EventDispatcher.dispatchEvent(this, "ItemSwipedLeft", position, id, title, artist, image, duration, liked);
  }

  @SimpleEvent(description = "Fired when the user swipes an item from left to right. Position is 1‑based.")
  public void ItemSwipedRight(int position, String id, String title, String artist, String image, String duration, boolean liked) {
    EventDispatcher.dispatchEvent(this, "ItemSwipedRight", position, id, title, artist, image, duration, liked);
  }

  @SimpleEvent(description = "Fired when the list is scrolled to the bottom (last item becomes visible).")
  public void BottomReached() {
    EventDispatcher.dispatchEvent(this, "BottomReached");
  }

  // ==================== LIFECYCLE ====================

  @SimpleFunction(description = "Anchors this component into the given container")
  public void AnchorTo(HVArrangement container) {
    ViewGroup viewGroup = (ViewGroup) listView.getParent();
    if (viewGroup != null) viewGroup.removeView(listView);
    ((ViewGroup) container.getView()).addView(listView);
  }

  // ==================== REFRESH ====================

  @SimpleFunction(description = "Forces the list to refresh (redraw).")
  public void RefreshList() {
    adapter.notifyDataSetChanged();
  }

  // ==================== CURRENT USER ====================

  @SimpleProperty(description = "Sets the current username for like detection.")
  public void CurrentUser(String user) {
    currentUser = (user == null) ? "" : user;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the current username.")
  public String CurrentUser() {
    return currentUser;
  }

  // ==================== DATA METHODS ====================

  @SimpleFunction(description = "Clears the entire list and replaces it with items from a YailList of [id, title, artist, imageUrl, duration, likedByList]. Duplicate IDs are ignored within the new list.")
  public void ClearAndReplaceWithList(YailList list) {
    items.clear();
    originalItems.clear();
    imageCache.clear();
    for (Object o : list.toArray()) {
      if (o instanceof YailList) {
        YailList itemData = (YailList) o;
        if (itemData.size() < 4) continue;
        String id = itemData.getString(0);
        String t = itemData.getString(1);
        String a = itemData.getString(2);
        String u = itemData.getString(3);
        String d = itemData.size() > 4 ? itemData.getString(4) : "";
        YailList likedByList = (itemData.size() > 5 && itemData.get(5) instanceof YailList) ?
            (YailList) itemData.get(5) : YailList.makeEmptyList();
        String img = (u == null || u.trim().isEmpty()) ? genericImageUrl : u;
        List<String> likedBy = new ArrayList<>();
        for (Object name : likedByList.toArray()) {
          likedBy.add(name.toString());
        }
        // Check duplicate within new list
        boolean duplicate = false;
        for (Item existing : items) {
          if (existing.id != null && existing.id.equals(id)) {
            duplicate = true;
            break;
          }
        }
        if (!duplicate) {
          Item newItem = new Item(id, img, t, a, d, likedBy, false);
          items.add(newItem);
          originalItems.add(newItem);
        }
      }
    }
    selectedPosition = -1;
    adapter.notifyDataSetChanged();
  }

  @SimpleFunction(description = "Adds a music item with a unique ID, image, title, artist, duration, and a list of users who liked it. Duplicates (same ID) are ignored.")
  public void AddMusicItemWithId(String id, String image, String title, String artist, String duration, YailList likedBy) {
    if (id == null || id.trim().isEmpty()) return;
    String img = (image == null || image.trim().isEmpty()) ? genericImageUrl : image;
    List<String> likedByList = new ArrayList<>();
    for (Object obj : likedBy.toArray()) {
      likedByList.add(obj.toString());
    }
    Item newItem = new Item(id, img, title, artist, duration, likedByList, false);

    // Check for duplicate ID
    for (Item existing : items) {
      if (existing.id != null && existing.id.equals(id)) {
        return; // duplicate found
      }
    }

    items.add(newItem);
    originalItems.add(newItem);
    adapter.notifyDataSetChanged();
  }

  //  Add item with likedBy as JSON string
  @SimpleFunction(description = "Adds a music item with a unique ID, image, title, artist, duration, and a JSON string representing the list of users who liked it. Duplicates (same ID) are ignored.")
  public void AddMusicItemWithLikesJson(String id, String image, String title, String artist, String duration, String likedByJson) {
    if (id == null || id.trim().isEmpty()) return;
    String img = (image == null || image.trim().isEmpty()) ? genericImageUrl : image;
    List<String> likedByList = new ArrayList<>();
    if (likedByJson != null && !likedByJson.trim().isEmpty()) {
      try {
        JSONArray jsonArray = new JSONArray(likedByJson);
        for (int i = 0; i < jsonArray.length(); i++) {
          likedByList.add(jsonArray.getString(i));
        }
      } catch (JSONException e) {
        // Ignore – leave list empty
      }
    }
    // Reuse the existing method to avoid duplicate code
    AddMusicItemWithId(id, img, title, artist, duration, YailList.makeList(likedByList));
  }

  @SimpleFunction(description = "Adds a music item with ID, image, title, artist, duration, and initial liked state (for current user).")
  public void AddMusicItem(String id, String image, String title, String artist, String duration, boolean liked) {
    List<String> likedBy = new ArrayList<>();
    if (liked && !currentUser.isEmpty()) {
      likedBy.add(currentUser);
    }
    AddMusicItemWithId(id, image, title, artist, duration, YailList.makeList(likedBy));
  }

  // Legacy methods – add without ID (duplicate check by title+artist)
  @SimpleFunction(description = "Legacy: Adds a music item without ID. Duplicates (same title & artist) are ignored.")
  public void AddMusicItemWithLikes(String image, String title, String artist, String duration, YailList likedBy) {
    String img = (image == null || image.trim().isEmpty()) ? genericImageUrl : image;
    List<String> likedByList = new ArrayList<>();
    for (Object obj : likedBy.toArray()) {
      likedByList.add(obj.toString());
    }
    for (Item existing : items) {
      if (existing.title.equalsIgnoreCase(title) && existing.artist.equalsIgnoreCase(artist)) {
        return; // duplicate found
      }
    }
    Item newItem = new Item(null, img, title, artist, duration, likedByList, false);
    items.add(newItem);
    originalItems.add(newItem);
    adapter.notifyDataSetChanged();
  }

  @SimpleFunction(description = "Legacy: Adds a music item without ID.")
  public void AddMusicItem(String image, String title, String artist, String duration, boolean liked) {
    List<String> likedBy = new ArrayList<>();
    if (liked && !currentUser.isEmpty()) {
      likedBy.add(currentUser);
    }
    AddMusicItemWithLikes(image, title, artist, duration, YailList.makeList(likedBy));
  }

  @SimpleFunction(description = "Legacy: Adds an item (image, title, subtitle).")
  public void AddItem(String image, String title, String subtitle) {
    AddMusicItem(image, title, subtitle, "", false);
  }

  @SimpleFunction(description = "Clears all items and image cache.")
  public void ClearList() {
    items.clear();
    originalItems.clear();
    imageCache.clear();
    selectedPosition = -1;
    adapter.notifyDataSetChanged();
  }

  @SimpleFunction(description = "Removes item at the specified 1‑based position.")
  public void RemoveItem(int position) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      items.remove(zeroBased);
      originalItems.remove(zeroBased);
      if (selectedPosition == zeroBased) selectedPosition = -1;
      else if (selectedPosition > zeroBased) selectedPosition--;
      adapter.notifyDataSetChanged();
    }
  }

  @SimpleFunction(description = "Updates an existing music item with new likedBy list. Position is 1‑based.")
  public void UpdateMusicItemWithLikes(int position, String image, String title, String artist, String duration, YailList likedBy) {
    int zeroBased = position - 1;
    if (zeroBased < 0 || zeroBased >= items.size()) return;
    String img = (image == null || image.trim().isEmpty()) ? genericImageUrl : image;
    Item it = items.get(zeroBased);
    it.image = img;
    it.title = title;
    it.artist = artist;
    it.duration = duration;
    it.likedBy.clear();
    for (Object obj : likedBy.toArray()) {
      it.likedBy.add(obj.toString());
    }
    adapter.notifyDataSetChanged();
  }

  @SimpleFunction(description = "Updates an existing music item with simple liked flag (for current user). Position is 1‑based.")
  public void UpdateMusicItem(int position, String image, String title, String artist, String duration, boolean liked) {
    int zeroBased = position - 1;
    if (zeroBased < 0 || zeroBased >= items.size()) return;
    String img = (image == null || image.trim().isEmpty()) ? genericImageUrl : image;
    Item it = items.get(zeroBased);
    it.image = img;
    it.title = title;
    it.artist = artist;
    it.duration = duration;
    if (liked && !currentUser.isEmpty()) {
      if (!it.likedBy.contains(currentUser)) {
        it.likedBy.add(currentUser);
      }
    } else {
      it.likedBy.remove(currentUser);
    }
    adapter.notifyDataSetChanged();
  }

  @SimpleFunction(description = "Sets the list of users who liked the specified item. Position is 1‑based.")
  public void SetLikedBy(int position, YailList likedBy) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      Item it = items.get(zeroBased);
      it.likedBy.clear();
      for (Object obj : likedBy.toArray()) {
        it.likedBy.add(obj.toString());
      }
      adapter.notifyDataSetChanged();
    }
  }

  @SimpleFunction(description = "Returns the list of users who liked the specified item. Position is 1‑based.")
  public YailList GetLikedBy(int position) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      return YailList.makeList(items.get(zeroBased).likedBy);
    }
    return YailList.makeEmptyList();
  }

  //  Get likedBy as JSON string
  @SimpleFunction(description = "Returns the list of users who liked the specified item as a JSON string. Position is 1‑based.")
  public String GetLikedByJson(int position) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      List<String> likedBy = items.get(zeroBased).likedBy;
      JSONArray jsonArray = new JSONArray(likedBy);
      return jsonArray.toString();
    }
    return "[]";
  }

  // ==================== PLAY/PAUSE CONTROL ====================

  @SimpleFunction(description = "Sets the playing state of the item at the given 1‑based position (true = playing, false = paused).")
  public void SetPlaying(int position, boolean playing) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      items.get(zeroBased).isPlaying = playing;
      adapter.notifyDataSetChanged();
    }
  }

  @SimpleFunction(description = "Returns the playing state of the item at the given 1‑based position.")
  public boolean GetPlaying(int position) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      return items.get(zeroBased).isPlaying;
    }
    return false;
  }

  // ==================== UNIFIED METHODS (TRIPLE KEY) ====================

  private int findItemIndexByTriple(String id, String title, String artist) {
    for (int i = 0; i < items.size(); i++) {
      Item it = items.get(i);
      boolean idMatch = (id == null && it.id == null) || (id != null && id.equals(it.id));
      boolean titleMatch = title != null && title.equals(it.title);
      boolean artistMatch = artist != null && artist.equals(it.artist);
      if (idMatch && titleMatch && artistMatch) {
        return i;
      }
    }
    return -1;
  }

  @SimpleFunction(description = "Selects the item matching the given id, title, and artist (without scrolling). Requires exact match for all three.")
  public void SelectItemByIdTitleArtist(String id, String title, String artist) {
    SelectItemByIdTitleArtist(id, title, artist, false);
  }

  @SimpleFunction(description = "Selects the item matching the given id, title, and artist and optionally scrolls to it.")
  public void SelectItemByIdTitleArtist(String id, String title, String artist, boolean scrollToIt) {
    int index = findItemIndexByTriple(id, title, artist);
    if (index != -1) {
      selectedPosition = index;
      adapter.notifyDataSetChanged();
      if (scrollToIt) {
        listView.smoothScrollToPosition(index);
      }
    }
  }

  @SimpleFunction(description = "Selects the item at the given 1‑based position and sets its playing state (true = playing, false = paused).")
  public void SelectItemWithPlaying(int position, boolean playing) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      selectedPosition = zeroBased;
      items.get(zeroBased).isPlaying = playing;
      adapter.notifyDataSetChanged();
    }
  }

  @SimpleFunction(description = "Selects the item at the given 1‑based position, sets its playing state, and optionally scrolls to it.")
  public void SelectItemWithPlaying(int position, boolean playing, boolean scrollToIt) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      selectedPosition = zeroBased;
      items.get(zeroBased).isPlaying = playing;
      adapter.notifyDataSetChanged();
      if (scrollToIt) {
        listView.smoothScrollToPosition(zeroBased);
      }
    }
  }

  @SimpleFunction(description = "Selects the item matching id, title, artist and sets its playing state.")
  public void SelectItemByIdTitleArtistWithPlaying(String id, String title, String artist, boolean playing) {
    SelectItemByIdTitleArtistWithPlaying(id, title, artist, playing, false);
  }

  @SimpleFunction(description = "Selects the item matching id, title, artist, sets its playing state, and optionally scrolls to it.")
  public void SelectItemByIdTitleArtistWithPlaying(String id, String title, String artist, boolean playing, boolean scrollToIt) {
    int index = findItemIndexByTriple(id, title, artist);
    if (index != -1) {
      selectedPosition = index;
      items.get(index).isPlaying = playing;
      adapter.notifyDataSetChanged();
      if (scrollToIt) {
        listView.smoothScrollToPosition(index);
      }
    }
  }

  // ==================== SEARCH ====================

  @SimpleFunction(description = "Filters the list by query (case‑insensitive) on title and artist. Empty query shows all items.")
  public void Search(String query) {
    if (query == null || query.trim().isEmpty()) {
      items.clear();
      items.addAll(originalItems);
    } else {
      String q = query.toLowerCase(Locale.getDefault());
      List<Item> filtered = new ArrayList<>();
      for (Item it : originalItems) {
        if (it.title.toLowerCase(Locale.getDefault()).contains(q)
            || it.artist.toLowerCase(Locale.getDefault()).contains(q)) {
          filtered.add(it);
        }
      }
      items.clear();
      items.addAll(filtered);
    }
    adapter.notifyDataSetChanged();
  }

  // ==================== SELECTION & SCROLLING (1‑based) ====================

  @SimpleFunction(description = "Selects the item at the given 1‑based position (without scrolling).")
  public void SelectItem(int position) {
    SelectItem(position, false);
  }

  @SimpleFunction(description = "Selects the item at the given 1‑based position and optionally scrolls to it.")
  public void SelectItem(int position, boolean scrollToIt) {
    int zeroBased = position - 1;
    if (zeroBased >= -1 && zeroBased < items.size()) {
      selectedPosition = zeroBased;
      adapter.notifyDataSetChanged();
      if (scrollToIt && zeroBased >= 0) {
        listView.smoothScrollToPosition(zeroBased);
      }
    }
  }

  @SimpleFunction(description = "Scrolls the list to make the given 1‑based position visible.")
  public void ScrollToPosition(int position) {
    int zeroBased = position - 1;
    if (zeroBased >= 0 && zeroBased < items.size()) {
      listView.smoothScrollToPosition(zeroBased);
    }
  }

  @SimpleFunction(description = "Scrolls the list to the last item.")
  public void ScrollToBottom() {
    if (items.size() > 0) {
      listView.smoothScrollToPosition(items.size() - 1);
    }
  }

  @SimpleProperty(description = "Returns the currently selected position (1‑based, -1 if none).")
  public int SelectedPosition() {
    return selectedPosition + 1;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFE0E0E0")
  @SimpleProperty(description = "Background color of the selected item.")
  public void HighlightColor(int color) {
    highlightColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the highlight color of the selected item.")
  public int HighlightColor() {
    return highlightColor;
  }

  // ==================== ROW HEIGHT ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER, defaultValue = "0")
  @SimpleProperty(description = "Sets the row height in pixels (0 = wrap content).")
  public void RowHeight(int height) {
    rowHeight = height;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the row height in pixels.")
  public int RowHeight() {
    return rowHeight;
  }

  // ==================== IMAGE DIMENSIONS ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER, defaultValue = "64")
  @SimpleProperty(description = "Sets the image width in pixels.")
  public void ImageWidth(int width) {
    imageWidth = width;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the image width.")
  public int ImageWidth() {
    return imageWidth;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER, defaultValue = "64")
  @SimpleProperty(description = "Sets the image height in pixels.")
  public void ImageHeight(int height) {
    imageHeight = height;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the image height.")
  public int ImageHeight() {
    return imageHeight;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER, defaultValue = "0")
  @SimpleProperty(description = "Sets the corner radius for images in pixels (0 = square).")
  public void CornerRadius(int radius) {
    cornerRadius = radius;
    imageCache.clear();
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the corner radius for images.")
  public int CornerRadius() {
    return cornerRadius;
  }

  // ==================== TITLE PROPERTIES ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
  @SimpleProperty(description = "Sets the title text color.")
  public void TitleTextColor(int color) {
    titleColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the title text color.")
  public int TitleTextColor() {
    return titleColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT, defaultValue = "16.0")
  @SimpleProperty(description = "Sets the title font size in sp.")
  public void TitleFontSize(float size) {
    titleTextSize = size;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the title font size.")
  public float TitleFontSize() {
    return titleTextSize;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TYPEFACE, defaultValue = Component.TYPEFACE_DEFAULT)
  @SimpleProperty(description = "Sets the title font typeface.")
  public void TitleFontTypeface(String typeface) {
    titleFontTypeface = typeface;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the title font typeface.")
  public String TitleFontTypeface() {
    return titleFontTypeface;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty(description = "Sets whether the title is bold.")
  public void TitleFontBold(boolean bold) {
    titleFontBold = bold;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether the title is bold.")
  public boolean TitleFontBold() {
    return titleFontBold;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty(description = "Sets whether the title is italic.")
  public void TitleFontItalic(boolean italic) {
    titleFontItalic = italic;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether the title is italic.")
  public boolean TitleFontItalic() {
    return titleFontItalic;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "false")
  @SimpleProperty(description = "Sets whether the title text should be interpreted as HTML.")
  public void TitleHTML(boolean html) {
    titleHtml = html;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether title HTML is enabled.")
  public boolean TitleHTML() {
    return titleHtml;
  }

  // ==================== ARTIST PROPERTIES ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF444444")
  @SimpleProperty(description = "Sets the artist text color.")
  public void ArtistTextColor(int color) {
    artistColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the artist text color.")
  public int ArtistTextColor() {
    return artistColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT, defaultValue = "14.0")
  @SimpleProperty(description = "Sets the artist font size in sp.")
  public void ArtistFontSize(float size) {
    artistTextSize = size;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the artist font size.")
  public float ArtistFontSize() {
    return artistTextSize;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TYPEFACE, defaultValue = Component.TYPEFACE_DEFAULT)
  @SimpleProperty(description = "Sets the artist font typeface.")
  public void ArtistFontTypeface(String typeface) {
    artistFontTypeface = typeface;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the artist font typeface.")
  public String ArtistFontTypeface() {
    return artistFontTypeface;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty(description = "Sets whether the artist text is bold.")
  public void ArtistFontBold(boolean bold) {
    artistFontBold = bold;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether the artist text is bold.")
  public boolean ArtistFontBold() {
    return artistFontBold;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty(description = "Sets whether the artist text is italic.")
  public void ArtistFontItalic(boolean italic) {
    artistFontItalic = italic;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether the artist text is italic.")
  public boolean ArtistFontItalic() {
    return artistFontItalic;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "false")
  @SimpleProperty(description = "Sets whether the artist text should be interpreted as HTML.")
  public void ArtistHTML(boolean html) {
    artistHtml = html;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether artist HTML is enabled.")
  public boolean ArtistHTML() {
    return artistHtml;
  }

  // ==================== DURATION PROPERTIES ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF888888")
  @SimpleProperty(description = "Sets the text color for the duration field.")
  public void DurationTextColor(int color) {
    durationColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the duration text color.")
  public int DurationTextColor() {
    return durationColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT, defaultValue = "12.0")
  @SimpleProperty(description = "Sets the text size (in sp) for the duration field.")
  public void DurationFontSize(float size) {
    durationTextSize = size;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the duration font size.")
  public float DurationFontSize() {
    return durationTextSize;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TYPEFACE, defaultValue = Component.TYPEFACE_DEFAULT)
  @SimpleProperty(description = "Sets the font typeface for the duration field.")
  public void DurationFontTypeface(String typeface) {
    durationFontTypeface = typeface;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the duration font typeface.")
  public String DurationFontTypeface() {
    return durationFontTypeface;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty(description = "Sets whether the duration is bold.")
  public void DurationFontBold(boolean bold) {
    durationFontBold = bold;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether the duration is bold.")
  public boolean DurationFontBold() {
    return durationFontBold;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty(description = "Sets whether the duration is italic.")
  public void DurationFontItalic(boolean italic) {
    durationFontItalic = italic;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns whether the duration is italic.")
  public boolean DurationFontItalic() {
    return durationFontItalic;
  }

  // ==================== BUTTON TEXT PROPERTIES ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "\uE2C4")
  @SimpleProperty(description = "Text for the download button (Material icon or custom).")
  public void DownloadButtonText(String text) {
    downloadButtonText = text;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the download button text.")
  public String DownloadButtonText() {
    return downloadButtonText;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "\uE87D")
  @SimpleProperty(description = "Text for the like button when not liked.")
  public void LikeButtonText(String text) {
    likeButtonText = text;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the like button text when not liked.")
  public String LikeButtonText() {
    return likeButtonText;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "\uE87E")
  @SimpleProperty(description = "Text for the like button when liked.")
  public void LikedButtonText(String text) {
    likedButtonText = text;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the liked button text.")
  public String LikedButtonText() {
    return likedButtonText;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "\uE037")
  @SimpleProperty(description = "Text for the play icon (Material icon).")
  public void PlayIconText(String text) {
    playIconText = text;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the play icon text.")
  public String PlayIconText() {
    return playIconText;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "\uE034")
  @SimpleProperty(description = "Text for the pause icon (Material icon).")
  public void PauseIconText(String text) {
    pauseIconText = text;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the pause icon text.")
  public String PauseIconText() {
    return pauseIconText;
  }

  // ==================== BUTTON COLORS ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
  @SimpleProperty(description = "Color for the download button.")
  public void DownloadButtonColor(int color) {
    downloadButtonColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the download button color.")
  public int DownloadButtonColor() {
    return downloadButtonColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
  @SimpleProperty(description = "Color for the like button when not liked.")
  public void LikeButtonColor(int color) {
    likeButtonColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the like button color when not liked.")
  public int LikeButtonColor() {
    return likeButtonColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFFF0000")
  @SimpleProperty(description = "Color for the like button when liked.")
  public void LikedButtonColor(int color) {
    likedButtonColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the liked button color.")
  public int LikedButtonColor() {
    return likedButtonColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
  @SimpleProperty(description = "Color for the play/pause button.")
  public void PlayButtonColor(int color) {
    playButtonColor = color;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the play/pause button color.")
  public int PlayButtonColor() {
    return playButtonColor;
  }

  // ==================== BUTTON TEXT SIZE ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT, defaultValue = "18.0")
  @SimpleProperty(description = "Text size for download/like/play buttons (in sp).")
  public void ButtonTextSize(float size) {
    buttonTextSize = size;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns button text size.")
  public float ButtonTextSize() {
    return buttonTextSize;
  }

  // ==================== SCROLL ANIMATION ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "True")
  @SimpleProperty(description = "Enable/disable slide-in animation when items become visible.")
  public void ScrollAnimationEnabled(boolean enabled) {
    scrollAnimationEnabled = enabled;
  }

  @SimpleProperty(description = "Returns whether scroll animation is enabled.")
  public boolean ScrollAnimationEnabled() {
    return scrollAnimationEnabled;
  }

  // ==================== BASIC APPEARANCE ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = Component.DEFAULT_VALUE_COLOR_NONE)
  @SimpleProperty(description = "Background color of the list.")
  public void BackgroundColor(int color) {
    bgColor = color;
    listView.setBackgroundColor(color);
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the background color of the list.")
  public int BackgroundColor() {
    return bgColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&H80FFFFFF")
  @SimpleProperty(description = "Sets the divider color.")
  public void DividerColor(int color) {
    dividerColor = color;
    listView.setDivider(new ColorDrawable(color));
  }

  @SimpleProperty(description = "Returns the divider color.")
  public int DividerColor() {
    return dividerColor;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER, defaultValue = "1")
  @SimpleProperty(description = "Sets the divider height in pixels.")
  public void DividerHeight(int height) {
    dividerHeight = height;
    listView.setDividerHeight(height);
  }

  @SimpleProperty(description = "Returns the divider height in pixels.")
  public int DividerHeight() {
    return dividerHeight;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER, defaultValue = "1")
  @SimpleProperty(description = "Sets image side: 1=Left, 2=Right.")
  public void ImageSide(int side) {
    imageSide = side;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the image side: 1=Left, 2=Right.")
  public int ImageSide() {
    return imageSide;
  }

  // ==================== FALLBACK IMAGES ====================

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "https://cdn-icons-png.flaticon.com/512/7500/7500224.png")
  @SimpleProperty(description = "Sets the placeholder image URL shown while loading.")
  public void PlaceholderImageUrl(String url) {
    placeholderImageUrl = url;
  }

  @SimpleProperty(description = "Returns the placeholder image URL.")
  public String PlaceholderImageUrl() {
    return placeholderImageUrl;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "https://cdn-icons-png.flaticon.com/512/2748/2748558.png")
  @SimpleProperty(description = "Sets the error image URL shown when loading fails.")
  public void ErrorImageUrl(String url) {
    errorImageUrl = url;
  }

  @SimpleProperty(description = "Returns the error image URL.")
  public String ErrorImageUrl() {
    return errorImageUrl;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "https://cdn-icons-png.flaticon.com/512/7500/7500224.png")
  @SimpleProperty(description = "Sets the generic image URL used when an item's image URL is empty.")
  public void GenericImageUrl(String url) {
    genericImageUrl = url;
    adapter.notifyDataSetChanged();
  }

  @SimpleProperty(description = "Returns the generic image URL.")
  public String GenericImageUrl() {
    return genericImageUrl;
  }

  // ==================== LISTENERS ====================

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    if (position >= 0 && position < items.size()) {
      selectedPosition = position;
      adapter.notifyDataSetChanged();
      Item it = items.get(position);
      boolean liked = it.likedBy.contains(currentUser);
      ItemSelected(position + 1, it.id == null ? "" : it.id, it.title, it.artist, it.image, it.duration, liked);
    }
  }

  @Override
  public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
    if (position >= 0 && position < items.size()) {
      Item it = items.get(position);
      boolean liked = it.likedBy.contains(currentUser);
      ItemLongClick(position + 1, it.id == null ? "" : it.id, it.title, it.artist, it.image, it.duration, liked);
      return true;
    }
    return false;
  }

  @Override
  public void onScrollStateChanged(AbsListView view, int scrollState) {
    // Not needed
  }

  @Override
  public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    // Detect bottom
    if (totalItemCount > 0 && firstVisibleItem + visibleItemCount >= totalItemCount) {
      if (!bottomReachedEventFired) {
        bottomReachedEventFired = true;
        BottomReached();
      }
    } else {
      bottomReachedEventFired = false;
    }

    // For animation direction
    if (firstVisibleItem > lastFirstVisibleItem) {
      // scrolling down
    } else if (firstVisibleItem < lastFirstVisibleItem) {
      // scrolling up
    }
    lastFirstVisibleItem = firstVisibleItem;
  }

  // ==================== HELPER METHODS ====================

  private Typeface resolveTypeface(String typeface) {
    switch (typeface) {
      case Component.TYPEFACE_SERIF: return Typeface.SERIF;
      case Component.TYPEFACE_SANSSERIF: return Typeface.SANS_SERIF;
      case Component.TYPEFACE_MONOSPACE: return Typeface.MONOSPACE;
      default: return Typeface.DEFAULT;
    }
  }

  private Drawable applyRounding(Drawable drawable, int radius) {
    if (drawable == null || radius <= 0) return drawable;
    try {
      Bitmap bitmap;
      if (drawable instanceof BitmapDrawable) {
        bitmap = ((BitmapDrawable) drawable).getBitmap();
      } else {
        return drawable;
      }
      RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
      rounded.setCornerRadius(radius);
      return rounded;
    } catch (Exception e) {
      return drawable;
    }
  }

  // ==================== ADAPTER ====================

  private class Adapter extends BaseAdapter {
    @Override
    public int getCount() { return items.size(); }
    @Override
    public Object getItem(int position) { return items.get(position); }
    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      ViewHolder holder;
      if (convertView == null) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);

        ViewGroup.LayoutParams lp;
        if (rowHeight > 0) {
          lp = new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        } else {
          lp = new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        layout.setLayoutParams(lp);
        layout.setBackgroundColor(bgColor);

        ImageView iv = new ImageView(context);
        LinearLayout.LayoutParams ivp = new LinearLayout.LayoutParams(imageWidth, imageHeight);
        ivp.gravity = Gravity.CENTER_VERTICAL;
        ivp.setMargins(8, 8, 8, 8);
        iv.setLayoutParams(ivp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textContainer.setLayoutParams(textParams);
        int pad = (int) (8 * context.getResources().getDisplayMetrics().density);
        textContainer.setPadding(pad, pad / 2, pad, pad / 2);

        TextView tvTitle = new TextView(context);
        TextView tvArtist = new TextView(context);
        textContainer.addView(tvTitle);
        textContainer.addView(tvArtist);

        LinearLayout rightContainer = new LinearLayout(context);
        rightContainer.setOrientation(LinearLayout.HORIZONTAL);
        rightContainer.setGravity(Gravity.CENTER_VERTICAL);
        rightContainer.setPadding(8, 0, 8, 0);

        TextView tvDuration = new TextView(context);
        tvDuration.setGravity(Gravity.CENTER_VERTICAL);
        rightContainer.addView(tvDuration);

        TextView btnPlayPause = new TextView(context);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        playParams.gravity = Gravity.CENTER_VERTICAL;
        playParams.setMargins(8, 0, 8, 0);
        btnPlayPause.setLayoutParams(playParams);
        btnPlayPause.setGravity(Gravity.CENTER);
        btnPlayPause.setTypeface(materialIconsTypeface);
        rightContainer.addView(btnPlayPause);

        TextView btnDownload = new TextView(context);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.CENTER_VERTICAL;
        btnParams.setMargins(8, 0, 8, 0);
        btnDownload.setLayoutParams(btnParams);
        btnDownload.setGravity(Gravity.CENTER);
        btnDownload.setTypeface(materialIconsTypeface);
        rightContainer.addView(btnDownload);

        TextView btnLike = new TextView(context);
        btnLike.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        btnLike.setGravity(Gravity.CENTER);
        btnLike.setTypeface(materialIconsTypeface);
        rightContainer.addView(btnLike);

        if (imageSide == 1) {
          layout.addView(iv);
          layout.addView(textContainer);
          layout.addView(rightContainer);
        } else {
          layout.addView(textContainer);
          layout.addView(rightContainer);
          layout.addView(iv);
        }

        holder = new ViewHolder();
        holder.layout = layout;
        holder.imageView = iv;
        holder.titleView = tvTitle;
        holder.artistView = tvArtist;
        holder.durationView = tvDuration;
        holder.playPauseButton = btnPlayPause;
        holder.downloadButton = btnDownload;
        holder.likeButton = btnLike;
        holder.hasAnimated = false;
        layout.setTag(holder);
        convertView = layout;
      } else {
        holder = (ViewHolder) convertView.getTag();
      }

      final Item it = items.get(position);
      final boolean likedByCurrentUser = it.likedBy.contains(currentUser);

      if (position == selectedPosition) {
        holder.layout.setBackgroundColor(highlightColor);
      } else {
        holder.layout.setBackgroundColor(bgColor);
      }

      // Title
      if (titleHtml) holder.titleView.setText(HtmlCompat.fromHtml(it.title, HtmlCompat.FROM_HTML_MODE_LEGACY));
      else holder.titleView.setText(it.title);
      holder.titleView.setTextSize(titleTextSize);
      int titleStyle = (titleFontBold ? Typeface.BOLD : 0) | (titleFontItalic ? Typeface.ITALIC : 0);
      holder.titleView.setTypeface(Typeface.create(resolveTypeface(titleFontTypeface), titleStyle));
      holder.titleView.setTextColor(titleColor);

      // Artist
      if (artistHtml) holder.artistView.setText(HtmlCompat.fromHtml(it.artist, HtmlCompat.FROM_HTML_MODE_LEGACY));
      else holder.artistView.setText(it.artist);
      holder.artistView.setTextSize(artistTextSize);
      int artistStyle = (artistFontBold ? Typeface.BOLD : 0) | (artistFontItalic ? Typeface.ITALIC : 0);
      holder.artistView.setTypeface(Typeface.create(resolveTypeface(artistFontTypeface), artistStyle));
      holder.artistView.setTextColor(artistColor);

      // Duration
      holder.durationView.setText(it.duration);
      holder.durationView.setTextSize(durationTextSize);
      int durationStyle = (durationFontBold ? Typeface.BOLD : 0) | (durationFontItalic ? Typeface.ITALIC : 0);
      holder.durationView.setTypeface(Typeface.create(resolveTypeface(durationFontTypeface), durationStyle));
      holder.durationView.setTextColor(durationColor);

      // Load image (cached as rounded)
      holder.imageView.setTag(it.image);
      loadImage(it.image, holder.imageView);

      // Store current position in each button's tag
      holder.playPauseButton.setTag(KEY_POSITION, position);
      holder.downloadButton.setTag(KEY_POSITION, position);
      holder.likeButton.setTag(KEY_POSITION, position);

      // Play/Pause button – only visible on selected item
      if (position == selectedPosition) {
        holder.playPauseButton.setVisibility(View.VISIBLE);
        holder.playPauseButton.setText(it.isPlaying ? pauseIconText : playIconText);
        holder.playPauseButton.setTextSize(buttonTextSize);
        holder.playPauseButton.setTextColor(playButtonColor);
      } else {
        holder.playPauseButton.setVisibility(View.GONE);
      }

      // Download button
      holder.downloadButton.setText(downloadButtonText);
      holder.downloadButton.setTextSize(buttonTextSize);
      holder.downloadButton.setTextColor(downloadButtonColor);

      // Like button
      holder.likeButton.setText(likedByCurrentUser ? likedButtonText : likeButtonText);
      holder.likeButton.setTextSize(buttonTextSize);
      holder.likeButton.setTextColor(likedByCurrentUser ? likedButtonColor : likeButtonColor);

      // Click listeners – retrieve position from tag, fire events with ID
      holder.playPauseButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          int pos = (int) v.getTag(KEY_POSITION);
          Item item = items.get(pos);
          item.isPlaying = !item.isPlaying;
          holder.playPauseButton.setText(item.isPlaying ? pauseIconText : playIconText);
          PlayPauseClicked(pos + 1, item.id == null ? "" : item.id,
              item.title, item.artist, item.image, item.duration, item.isPlaying);
        }
      });

      holder.downloadButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          int pos = (int) v.getTag(KEY_POSITION);
          Item item = items.get(pos);
          DownloadClicked(pos + 1, item.id == null ? "" : item.id,
              item.title, item.artist, item.image, item.duration);
        }
      });

      holder.likeButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          int pos = (int) v.getTag(KEY_POSITION);
          Item item = items.get(pos);
          if (item.likedBy.contains(currentUser)) {
            item.likedBy.remove(currentUser);
          } else {
            if (!currentUser.isEmpty()) {
              item.likedBy.add(currentUser);
            }
          }
          boolean newLiked = item.likedBy.contains(currentUser);
          holder.likeButton.setText(newLiked ? likedButtonText : likeButtonText);
          holder.likeButton.setTextColor(newLiked ? likedButtonColor : likeButtonColor);
          LikeClicked(pos + 1, item.id == null ? "" : item.id,
              item.title, item.artist, item.image, item.duration, newLiked);
        }
      });

      // Swipe detection – fires events using item
      final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
          float diffX = e2.getX() - e1.getX();
          float diffY = e2.getY() - e1.getY();
          if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
            boolean liked = it.likedBy.contains(currentUser);
            if (diffX > 0) {
              ItemSwipedRight(position + 1, it.id == null ? "" : it.id,
                  it.title, it.artist, it.image, it.duration, liked);
            } else {
              ItemSwipedLeft(position + 1, it.id == null ? "" : it.id,
                  it.title, it.artist, it.image, it.duration, liked);
            }
            return true;
          }
          return false;
        }
      });

      holder.layout.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
          return gestureDetector.onTouchEvent(event);
        }
      });

      // Scroll animation
      if (scrollAnimationEnabled && !holder.hasAnimated) {
        int firstVisible = listView.getFirstVisiblePosition();
        boolean scrollingDown = (firstVisible > lastFirstVisibleItem);
        lastFirstVisibleItem = firstVisible;

        float fromX = scrollingDown ? -0.5f : 0.5f;
        Animation slide = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, fromX,
            Animation.RELATIVE_TO_SELF, 0,
            Animation.RELATIVE_TO_SELF, 0,
            Animation.RELATIVE_TO_SELF, 0);
        slide.setDuration(300);
        holder.layout.startAnimation(slide);
        holder.hasAnimated = true;
      }

      return convertView;
    }

    private void loadImage(final String url, final ImageView imageView) {
      if (url == null || url.isEmpty()) return;

      if (imageCache.containsKey(url)) {
        imageView.setImageDrawable(imageCache.get(url));
        imageView.invalidate();
        return;
      }

      imageView.setImageDrawable(null);

      executor.submit(new Runnable() {
        @Override
        public void run() {
          try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            InputStream is = conn.getInputStream();
            Drawable d = Drawable.createFromStream(is, null);
            is.close();
            if (d != null) {
              final Drawable rounded = applyRounding(d, cornerRadius);
              imageCache.put(url, rounded);
              mainHandler.post(new Runnable() {
                @Override
                public void run() {
                  if (url.equals(imageView.getTag())) {
                    imageView.setImageDrawable(rounded);
                    imageView.invalidate();
                  }
                }
              });
            } else {
              loadErrorImage(imageView, url);
            }
          } catch (Exception e) {
            loadErrorImage(imageView, url);
          }
        }
      });
    }

    private void loadErrorImage(final ImageView imageView, final String originalUrl) {
      if (errorImageUrl == null || errorImageUrl.isEmpty()) return;
      if (imageCache.containsKey(errorImageUrl)) {
        imageView.setImageDrawable(imageCache.get(errorImageUrl));
        imageView.invalidate();
        return;
      }
      executor.submit(new Runnable() {
        @Override
        public void run() {
          try {
            HttpURLConnection conn = (HttpURLConnection) new URL(errorImageUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            InputStream is = conn.getInputStream();
            Drawable d = Drawable.createFromStream(is, null);
            is.close();
            if (d != null) {
              final Drawable rounded = applyRounding(d, cornerRadius);
              imageCache.put(errorImageUrl, rounded);
              mainHandler.post(new Runnable() {
                @Override
                public void run() {
                  if (originalUrl.equals(imageView.getTag())) {
                    imageView.setImageDrawable(rounded);
                    imageView.invalidate();
                  }
                }
              });
            }
          } catch (Exception ignored) {}
        }
      });
    }
  }

  private static class ViewHolder {
    LinearLayout layout;
    ImageView imageView;
    TextView titleView;
    TextView artistView;
    TextView durationView;
    TextView playPauseButton;
    TextView downloadButton;
    TextView likeButton;
    boolean hasAnimated;
  }

  private static class Item {
    String id;
    String image;
    String title;
    String artist;
    String duration;
    List<String> likedBy;
    boolean isPlaying;

    Item(String id, String image, String title, String artist, String duration, List<String> likedBy, boolean isPlaying) {
      this.id = id;
      this.image = image;
      this.title = title;
      this.artist = artist;
      this.duration = duration;
      this.likedBy = (likedBy != null) ? likedBy : new ArrayList<String>();
      this.isPlaying = isPlaying;
    }
  }
}