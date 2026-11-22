package moe.xWey10.tiling;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import java.text.DateFormat;
import java.util.Date;

/**
 * Native settings and QS editor. Styling is staged; tile membership uses immediate native saves.
 */
@SuppressLint("SetTextI18n") // The requested first version intentionally has a Russian-only UI.
public final class MainActivity extends AppCompatActivity {
  private MaterialSwitch enabled, battery;
  private Slider size, gap, radius, angle;
  private Button undo, redo;
  private boolean bindingAngle, trackingAngle;
  private ProfileSheet profileSheet;
  private GitHubUpdates updates;
  private Button checkUpdates, openRelease, homeUpdate;
  private TextView updateStatus;
  private final Runnable refreshUpdates = this::refreshUpdateControls;
  private boolean restoredDraft;
  private String pendingExport = "";
  private String pendingTileInspection;
  private static final java.util.concurrent.ExecutorService PROFILE_IO =
      java.util.concurrent.Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "EssentialTiling-profiles");
            t.setDaemon(true);
            return t;
          });
  private final androidx.activity.result.ActivityResultLauncher<String> exportProfile =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
              "application/json"),
          uri -> {
            if (uri != null) writeProfile(uri);
            else pendingExport = "";
          });
  private final androidx.activity.result.ActivityResultLauncher<String[]> importProfile =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) readProfile(uri);
          });
  private MaterialButtonToggleGroup shape;
  private TextView sizeLabel, gapLabel, radiusLabel, status, message;
  private Button restart, apply, reset, discard;
  private ThemePalette palette;
  private LayoutClient layoutClient;
  private TileClient tileClient;
  private android.app.Dialog tileSheet;
  private TilePickerView tilePicker;
  private LinearLayout previewToolbar;
  private String layoutDraft = "";
  private String previewActionError = "";
  private QsLayout nativeSource;
  private QsLayout.Item selectedItem;
  private LinearLayout selectionControls;
  private TextView selectionLabel;
  private Button smallTile, wideTile, largeTile;
  private boolean committing, previewBusy;
  private boolean changingTiles;
  private boolean catalogLoading;
  private int catalogGeneration;
  private boolean trackingControl, previewDirty;
  private Runnable pendingPreviewUi;
  private LinearLayout content, editorContent, feedbackRow;
  private FrameLayout pageHost;
  private PreviewHost previewHost;
  private android.view.MenuItem restartAction, revertAction;
  private final View[] screens = new View[4];
  private MaterialToolbar appBar;
  private SettingsUi ui;
  private int screen;
  private BottomSheetDialog propertiesSheet;
  private TextView selectedTitle;
  private Button propertiesButton;
  private LinearProgressIndicator previewProgress;
  private boolean controlsReady, bindingControls;
  private String draftHint = "";
  private OnBackPressedCallback back;
  private PreviewView preview;
  private PreviewClient previewClient;
  private RestartClient restartClient;
  private TextView previewStatus, previewPage, tileActionStatus;
  private Button previousPage, nextPage;
  private int selectedPage, previewPages = 1;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        @Override
        public void run() {
          refreshStatus();
          handler.postDelayed(this, 2000);
        }
      };

  @android.annotation.SuppressLint(
      "AlwaysShowAction") // The single restart action must stay visible in the top-right corner.
  @Override
  public void onCreate(Bundle state) {
    setTheme(R.style.Theme_EssentialTiling);
    DynamicColors.applyToActivityIfAvailable(this);
    super.onCreate(state);
    palette = new ThemePalette(this);
    ui = new SettingsUi(this);
    updates = GitHubUpdates.get(this);
    layoutClient = new LayoutClient(this);
    tileClient = new TileClient(this);
    getWindow().setDecorFitsSystemWindows(false);
    LinearLayout root = ui.column();
    root.setBackgroundColor(palette.background);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          Insets bars =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    appBar = new MaterialToolbar(this);
    appBar.setNavigationIconTint(palette.text);
    appBar.setNavigationContentDescription("Назад");
    restartAction = appBar.getMenu().add(0, R.id.restart_systemui, 0, "Перезапустить SystemUI");
    restartAction.setIcon(R.drawable.ic_restart);
    restartAction.setIconTintList(android.content.res.ColorStateList.valueOf(palette.text));
    restartAction.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
    restartAction.setTooltipText(null);
    restartAction.setContentDescription("Перезапустить SystemUI");
    restartAction.setOnMenuItemClickListener(
        item -> {
          saveAndMaybeRestart(true);
          return true;
        });
    appBar
        .getMenu()
        .add("Профили")
        .setOnMenuItemClickListener(
            item -> {
              openProfiles();
              return true;
            });
    revertAction = appBar.getMenu().add("Отменить изменения");
    revertAction.setEnabled(false);
    revertAction.setOnMenuItemClickListener(
        item -> {
          revertDraft();
          return true;
        });
    root.addView(appBar, new LinearLayout.LayoutParams(-1, dp(64)));
    pageHost = new FrameLayout(this);
    root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = ui.column();
    footer.setPadding(dp(24), dp(4), dp(24), dp(8));
    feedbackRow = new LinearLayout(this);
    feedbackRow.setGravity(Gravity.CENTER_VERTICAL);
    message = text("", 13);
    message.setMaxLines(2);
    message.setMinLines(2);
    message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    feedbackRow.addView(
        message,
        new LinearLayout.LayoutParams(0, Math.max(dp(40), message.getLineHeight() * 2), 1));
    footer.addView(
        feedbackRow,
        new LinearLayout.LayoutParams(-1, Math.max(dp(48), message.getLineHeight() * 2 + dp(8))));
    apply = ui.button("Применить", true);
    apply.setSingleLine(true);
    apply.setOnClickListener(v -> saveAndMaybeRestart(!hasChanges() && needsRestart()));
    footer.addView(apply, new LinearLayout.LayoutParams(-1, dp(48)));
    root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    setContentView(root);
    buildHome();
    buildEditor();
    buildAppearance();
    buildModule();
    content = editorContent;
    preview.setListener(
        new PreviewView.Listener() {
          @Override
          public void edited(QsLayout model) {
            previewActionError = "";
            layoutDraft =
                nativeSource != null && !model.differsFrom(nativeSource) ? "" : model.json();
            discard.setVisibility(layoutDraft.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            previewClient.setLayout(layoutDraft);
            if (tilePicker != null) tilePicker.refresh();
            updateDraftState();
          }

          @Override
          public void selected(QsLayout.Item item) {
            selectedItem = item;
            updateSelection();
          }

          @Override
          public void history(boolean canUndo, boolean canRedo) {
            updateHistory();
          }

          @Override
          public void message(String text) {
            previewActionError = text;
            setPreviewStatus(text);
          }

          @Override
          public void focus(String id) {
            if (previewClient != null) previewClient.setFocus(id, preview.hasVariants(id));
          }

          @Override
          public void remove(String id) {
            tileOperation("remove", id.substring(2), 0, 0, 0);
          }

          @Override
          public void inspect(QsLayout.Item item) {
            inspectTile(item);
          }

          @Override
          public void empty(long page, int x, int y) {
            showTilePicker(page, x, y);
          }
        });
    previewClient =
        new PreviewClient(
            this,
            new PreviewClient.Listener() {
              @Override
              public void loading() {
                previewUi(
                    () -> {
                      setPreviewBusy(true);
                      setPreviewStatus("Обновление из SystemUI…");
                    });
              }

              @Override
              public void error(String text) {
                previewUi(
                    () -> {
                      setPreviewBusy(false);
                      setPreviewStatus(text);
                      if (tilePicker != null) {
                        tilePicker.frameReady();
                        tilePicker.message(text);
                      }
                    });
              }

              @Override
              public void frame(Bitmap bitmap, Bundle metadata) {
                previewUi(
                    () -> {
                      String oldCapability = preview.capabilityMessage();
                      preview.setFrame(bitmap, metadata);
                      setPreviewBusy(false);
                      if (!oldCapability.isEmpty()
                          && preview.capabilityMessage().isEmpty()
                          && previewActionError.equals(oldCapability)) previewActionError = "";
                      String source = metadata.getString("source", "");
                      nativeSource = source.isEmpty() ? null : QsLayout.parse(source);
                      preview.repairRotationFrames();
                      selectedPage = metadata.getInt("page");
                      previewPages = metadata.getInt("pages", 1);
                      previewClient.resolvedPage(selectedPage);
                      previewPage.setText((selectedPage + 1) + " / " + previewPages);
                      updateNavigation();
                      if (tilePicker != null) tilePicker.frameReady();
                      String feedback =
                          nativeSource == null
                              ? "Редактор сетки недоступен."
                              : !nativeSource.portrait
                                  ? "Для редактирования поверните телефон вертикально."
                                  : preview.capabilityMessage();
                      if (!previewActionError.isEmpty() && !feedback.equals(previewActionError))
                        feedback += (feedback.isEmpty() ? "" : "\n") + previewActionError;
                      if (metadata.containsKey("variantError"))
                        feedback +=
                            (feedback.isEmpty() ? "" : "\n") + metadata.getString("variantError");
                      setPreviewStatus(feedback);
                      updateDraftState();
                    });
              }
            });

    enabled.setOnCheckedChangeListener(
        (v, checked) -> {
          updateMasterSwitch();
          updatePreview();
        });
    battery.setOnCheckedChangeListener((v, checked) -> updatePreview());
    shape.addOnButtonCheckedListener(
        (group, id, checked) -> {
          if (checked) {
            updateRadiusVisibility();
            updatePreview();
          }
        });
    apply.setOnClickListener(v -> saveAndMaybeRestart(!hasChanges() && needsRestart()));
    reset.setOnClickListener(
        v -> {
          discardLayout();
          bind(Config.DEFAULT);
          if (saveDraft()) message.setText("Оформление сброшено. Перезапустите SystemUI.");
        });
    restartClient =
        new RestartClient(
            this,
            (success, text) -> {
              if (isDestroyed()) return;
              setCommitting(false);
              message.setText(text);
              refreshStatus();
              if (success) {
                previewActionError = "";
                preview.setFrame(null);
                previewClient.refresh();
              }
            });
    restart.setOnClickListener(v -> saveAndMaybeRestart(true));
    back =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            showScreen(0);
          }
        };
    getOnBackPressedDispatcher().addCallback(this, back);
    appBar.setNavigationOnClickListener(v -> showScreen(0));
    Config saved = SettingsStore.read(this);
    if (state == null) {
      DraftStore.Draft remembered = DraftStore.read(this);
      if (remembered != null) {
        restoredDraft = true;
        saved = remembered.config();
        layoutDraft = remembered.layout();
        discard.setVisibility(layoutDraft.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        previewClient.setLayout(layoutDraft);
      }
    }
    if (state != null) {
      selectedPage = state.getInt("preview_page", 0);
      previewClient.setPage(selectedPage);
    }
    if (state != null) {
      layoutDraft = state.getString("layout_draft", "");
      pendingExport = state.getString("profile_export", "");
      preview.restoreHistorySnapshot(state.getString("edit_history", ""));
      discard.setVisibility(layoutDraft.isEmpty() ? View.INVISIBLE : View.VISIBLE);
      previewClient.setLayout(layoutDraft);
    }
    if (state != null)
      saved =
          new Config(
              state.getBoolean("enabled"),
              state.getInt("scale", 100),
              state.getInt("gap"),
              state.getInt("radius", -1),
              state.getBoolean("battery"),
              saved.revision);
    controlsReady = true;
    bind(saved);
    if (restoredDraft) message.setText("Черновик восстановлен. Изменения ещё не применены.");
    showScreen(state == null ? (restoredDraft ? 1 : 0) : state.getInt("screen", 0));
  }

  private LinearLayout screen(int id) {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    LinearLayout panel = ui.column();
    panel.setPadding(dp(16), 0, dp(16), dp(24));
    scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
    pageHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    screens[id] = scroll;
    return panel;
  }

  void showScreen(int id) {
    screen = Math.max(0, Math.min(3, id));
    for (int i = 0; i < screens.length; i++)
      screens[i].setVisibility(i == screen ? View.VISIBLE : View.GONE);
    appBar.setTitle(new String[] {"", "Редактор шторки", "Оформление", "Модуль"}[screen]);
    if (screen == 0) appBar.setNavigationIcon((android.graphics.drawable.Drawable) null);
    else appBar.setNavigationIcon(R.drawable.ic_back);
    if (back != null) back.setEnabled(screen != 0);
  }

  private void buildHome() {
    LinearLayout panel = screen(0);
    panel.addView(ui.heading("Шторка"));
    enabled = new MaterialSwitch(this);
    enabled.setText("Модификации");
    enabled.setTextAppearance(
        com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
    enabled.setPadding(dp(24), dp(20), dp(20), dp(20));
    enabled.setMinHeight(dp(88));
    enabled.setBackground(ui.surface(palette.container, 28));
    enabled.setTextColor(palette.onContainer);
    panel.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
    homeUpdate = ui.button("", false);
    homeUpdate.setVisibility(View.GONE);
    homeUpdate.setOnClickListener(v -> openUpdateRelease());
    panel.addView(homeUpdate, layout(8));
    panel.addView(ui.section("Настройки"));
    LinearLayout group = ui.group();
    group.addView(
        ui.row(
            "Редактор шторки", "Плитки и расположение", R.drawable.ic_grid, () -> showScreen(1)));
    group.addView(
        ui.row(
            "Оформление",
            "Скругление и дополнительные настройки",
            R.drawable.ic_palette,
            () -> showScreen(2)));
    group.addView(
        ui.row(
            "Профили",
            "Сохранить, загрузить и экспортировать",
            R.drawable.ic_profiles,
            this::openProfiles));
    group.addView(
        ui.row("Модуль", "Подключение и перезапуск", R.drawable.ic_module, () -> showScreen(3)));
    panel.addView(group);
  }

  private void updateMasterSwitch() {
    enabled.setBackground(
        ui.surface(enabled.isChecked() ? palette.container : palette.surface, 28));
    enabled.setTextColor(enabled.isChecked() ? palette.onContainer : palette.text);
  }

  private void buildEditor() {
    content = ui.column();
    content.setPadding(dp(16), 0, dp(16), dp(8));
    editorContent = content;
    pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    screens[1] = content;
    previewToolbar = ui.column();
    content.addView(previewToolbar, layout(4));
    LinearLayout navigation = new LinearLayout(this);
    navigation.setGravity(Gravity.CENTER_VERTICAL);
    Button update = ui.button("", false);
    update.setContentDescription("Обновить предпросмотр");
    ((MaterialButton) update).setIconResource(R.drawable.ic_refresh);
    update.setOnClickListener(
        v -> {
          if (committing) return;
          message.setText("");
          setTileStatus("");
          previewClient.refresh();
        });
    navigation.addView(update, navigationParams());
    previousPage = ui.button("‹", false);
    previousPage.setContentDescription("Предыдущая страница");
    nextPage = ui.button("›", false);
    nextPage.setContentDescription("Следующая страница");
    previewPage = text("1 / 1", 14);
    previewPage.setGravity(Gravity.CENTER);
    navigation.addView(previousPage, navigationParams());
    navigation.addView(previewPage, new LinearLayout.LayoutParams(0, dp(48), 1));
    navigation.addView(nextPage, navigationParams());
    previewToolbar.addView(navigation);
    previousPage.setEnabled(false);
    nextPage.setEnabled(false);
    previousPage.setOnClickListener(v -> selectPage(selectedPage - 1));
    nextPage.setOnClickListener(
        v -> {
          if (selectedPage + 1 < previewPages) selectPage(selectedPage + 1);
          else if (canAppendPage()) tileOperation("page", "", 0, 0, 0);
        });
    previewProgress = new LinearProgressIndicator(this);
    previewProgress.setIndeterminate(true);
    previewProgress.setVisibility(View.INVISIBLE);
    previewToolbar.addView(previewProgress, new LinearLayout.LayoutParams(-1, dp(4)));
    preview = new PreviewView(this);
    previewHost = new PreviewHost(this);
    previewHost.addView(preview, new FrameLayout.LayoutParams(-1, -2));
    LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, 0, 1);
    gridParams.topMargin = dp(8);
    content.addView(previewHost, gridParams);
    LinearLayout selected = new LinearLayout(this);
    selected.setGravity(Gravity.CENTER_VERTICAL);
    selected.setPadding(dp(4), dp(8), 0, dp(8));
    selectedTitle = text("Выберите плитку", 16);
    selectedTitle.setSingleLine(true);
    selectedTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    undo = ui.button("", false);
    redo = ui.button("", false);
    ((MaterialButton) undo).setIconResource(R.drawable.ic_undo);
    ((MaterialButton) redo).setIconResource(R.drawable.ic_redo);
    undo.setContentDescription("Отменить правку раскладки");
    redo.setContentDescription("Повторить правку раскладки");
    undo.setEnabled(false);
    redo.setEnabled(false);
    undo.setOnClickListener(v -> preview.undo());
    redo.setOnClickListener(v -> preview.redo());
    for (Button command : new Button[] {undo, redo}) {
      MaterialButton icon = (MaterialButton) command;
      icon.setInsetTop(dp(6));
      icon.setInsetBottom(dp(6));
      icon.setCornerRadius(dp(18));
      icon.setIconSize(dp(20));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
      p.setMarginEnd(dp(4));
      selected.addView(command, p);
    }
    selected.addView(selectedTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
    selectedTitle.setGravity(Gravity.CENTER_VERTICAL);
    propertiesButton = ui.button("Изменить", false);
    propertiesButton.setEnabled(false);
    propertiesButton.setOnClickListener(v -> openTileProperties());
    selected.addView(propertiesButton, new LinearLayout.LayoutParams(-2, dp(48)));
    content.addView(selected);
    Button add = ui.button("Добавить плитку", false);
    ((MaterialButton) add).setIconResource(R.drawable.ic_add);
    add.setOnClickListener(
        v -> {
          QsLayout model = preview.getLayoutDraft();
          if (model != null) showTilePicker(model.pages.get(selectedPage).id(), -1, -1);
        });
    navigation.addView(add, navigationParams());
    add.setText("");
    add.setContentDescription("Добавить плитку");
    for (Button action : new Button[] {update, previousPage, nextPage, add}) {
      MaterialButton compact = (MaterialButton) action;
      compact.setInsetTop(dp(6));
      compact.setInsetBottom(dp(6));
      compact.setCornerRadius(dp(18));
      compact.setIconSize(dp(20));
      compact.setMinWidth(0);
      compact.setMinimumWidth(0);
    }
    LinearLayout style = ui.group();
    style.setPadding(dp(12), 0, dp(8), 0);
    content.addView(style, layout(4));
    LinearLayout scaleRow = new LinearLayout(this);
    scaleRow.setGravity(Gravity.CENTER_VERTICAL);
    sizeLabel = text("Масштаб", 14);
    scaleRow.addView(sizeLabel, new LinearLayout.LayoutParams(dp(88), dp(48)));
    sizeLabel.setGravity(Gravity.CENTER_VERTICAL);
    size = sliderControl(80, 100, 100);
    size.setContentDescription("Масштаб плиток");
    scaleRow.addView(size, new LinearLayout.LayoutParams(0, dp(48), 1));
    style.addView(scaleRow);
    LinearLayout gapRow = new LinearLayout(this);
    gapRow.setGravity(Gravity.CENTER_VERTICAL);
    gapLabel = text("Интервалы", 14);
    gapRow.addView(gapLabel, new LinearLayout.LayoutParams(dp(88), dp(48)));
    gapLabel.setGravity(Gravity.CENTER_VERTICAL);
    gap = sliderControl(0, 12, 0);
    gap.setContentDescription("Интервалы");
    gapRow.addView(gap, new LinearLayout.LayoutParams(0, dp(48), 1));
    style.addView(gapRow);
    int labelWidth =
        Math.max(
            dp(88),
            Math.min(
                (int) (getResources().getDisplayMetrics().widthPixels * .4f),
                (int) Math.ceil(gapLabel.getPaint().measureText("Интервалы")) + dp(8)));
    for (TextView label : new TextView[] {sizeLabel, gapLabel}) {
      label.setSingleLine(true);
      label.setEllipsize(android.text.TextUtils.TruncateAt.END);
      label.getLayoutParams().width = labelWidth;
    }
    previewStatus = text("", 13);
    previewStatus.setMaxLines(2);
    previewStatus.setVisibility(View.GONE);
    content.addView(previewStatus, layout(8));
    tileActionStatus = text("", 13);
    tileActionStatus.setMaxLines(2);
    tileActionStatus.setVisibility(View.GONE);
    content.addView(tileActionStatus, layout(4));
    selectionControls = new LinearLayout(this);
    selectionControls.setOrientation(LinearLayout.VERTICAL);
    selectionLabel =
        ui.text("", com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
    selectionLabel.setSingleLine(true);
    selectionLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
    selectionControls.addView(selectionLabel);
    TextView rotationTitle = text("Поворот", 16);
    selectionControls.addView(rotationTitle, layout(16));
    angle = new Slider(this);
    angle.setContentDescription("Поворот плитки");
    angle.setValueFrom(-180);
    angle.setValueTo(180);
    angle.setStepSize(1);
    angle.setValue(0);
    angle.setLabelBehavior(LabelFormatter.LABEL_GONE);
    selectionControls.addView(angle, layout(0));
    angle.addOnChangeListener(
        (control, value, fromUser) -> {
          if (!bindingAngle) {
            control.setStateDescription(Math.round(value) + "°");
            preview.rotateSelected(value);
          }
        });
    angle.addOnSliderTouchListener(
        new Slider.OnSliderTouchListener() {
          public void onStartTrackingTouch(Slider bar) {
            trackingControl = true;
            trackingAngle = true;
            preview.beginEdit();
          }

          public void onStopTrackingTouch(Slider bar) {
            trackingAngle = false;
            preview.endEdit();
            finishControlTracking();
            updateSelection();
          }
        });
    LinearLayout turns = new LinearLayout(this);
    compactButton(
        turns,
        "−90°",
        "Повернуть на 90 градусов влево",
        () -> {
          if (selectedItem != null) preview.rotateSelected(selectedItem.form().rotation() - 90);
        });
    compactButton(turns, "0°", "Сбросить поворот", () -> preview.resetRotation());
    compactButton(
        turns,
        "+90°",
        "Повернуть на 90 градусов вправо",
        () -> {
          if (selectedItem != null) preview.rotateSelected(selectedItem.form().rotation() + 90);
        });
    selectionControls.addView(turns, layout(0));
    selectionControls.addView(ui.section("Размер"));
    LinearLayout sizes = new LinearLayout(this);
    smallTile = compactButton(sizes, "1×1", "Размер 1 на 1", () -> preview.resizeSelected(1, 1));
    wideTile = compactButton(sizes, "2×1", "Размер 2 на 1", () -> preview.resizeSelected(2, 1));
    largeTile = compactButton(sizes, "2×2", "Размер 2 на 2", () -> preview.resizeSelected(2, 2));
    compactButton(sizes, "1×3", "Размер 1 на 3", () -> preview.resizeSelected(1, 3));
    selectionControls.addView(sizes, layout(6));
    selectionControls.addView(ui.section("Форма"));
    LinearLayout forms = new LinearLayout(this);
    compactButton(forms, "L", "L-образная плитка", () -> preview.formSelected(true));
    compactButton(forms, "Прямоугольник", "Убрать вырез", () -> preview.formSelected(false));
    selectionControls.addView(forms, layout(6));
    LinearLayout extras = new LinearLayout(this);
    compactButton(extras, "↗", "Диагональная плитка вверх", () -> preview.diagonalSelected(true));
    compactButton(extras, "↘", "Диагональная плитка вниз", () -> preview.diagonalSelected(false));
    compactButton(extras, "Пимпочки", "Круглые выступы на плитке", () -> preview.nubsSelected());
    selectionControls.addView(extras, layout(6));
    selectionControls.addView(ui.section("Слои и поворот"));
    LinearLayout layers = new LinearLayout(this);
    compactButton(
        layers, "На передний план", "На передний план", () -> preview.layerSelected(true));
    compactButton(layers, "На задний план", "На задний план", () -> preview.layerSelected(false));
    selectionControls.addView(layers, layout(6));

    selectionControls.addView(ui.section("Перемещение"));
    LinearLayout directions = new LinearLayout(this);
    compactButton(directions, "←", "Сдвинуть влево", () -> preview.moveSelected(-1, 0));
    compactButton(directions, "↑", "Сдвинуть вверх", () -> preview.moveSelected(0, -1));
    compactButton(directions, "↓", "Сдвинуть вниз", () -> preview.moveSelected(0, 1));
    compactButton(directions, "→", "Сдвинуть вправо", () -> preview.moveSelected(1, 0));
    selectionControls.addView(directions, layout(6));
    content.addView(selectionControls, layout(8));
    selectionControls.setVisibility(View.GONE);

    discard = ui.button("", false);
    ((MaterialButton) discard).setIconResource(R.drawable.ic_reset);
    discard.setContentDescription("Отменить все несохранённые изменения");
    feedbackRow.addView(discard, new LinearLayout.LayoutParams(dp(48), dp(48)));
    discard.setVisibility(View.INVISIBLE);
    discard.setOnClickListener(v -> revertDraft());
  }

  private void buildAppearance() {
    content = screen(2);
    content.addView(ui.section("Форма"));
    LinearLayout group = ui.group();
    group.setPadding(dp(16), dp(16), dp(16), dp(8));
    content.addView(group);
    LinearLayout page = content;
    content = group;
    content.addView(text("Скругление", 16), layout(0));
    shape = new MaterialButtonToggleGroup(this);
    shape.setSingleSelection(true);
    shape.setSelectionRequired(true);
    MaterialButton stock =
        new MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    stock.setId(R.id.shape_stock);
    stock.setText("Штатное");
    MaterialButton custom =
        new MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    custom.setId(R.id.shape_custom);
    custom.setText("Своё");
    shape.addView(stock, new LinearLayout.LayoutParams(0, dp(48), 1));
    shape.addView(custom, new LinearLayout.LayoutParams(0, dp(48), 1));
    shape.check(R.id.shape_stock);
    content.addView(shape, layout(12));
    radiusLabel = text("Радиус", 14);
    radiusLabel.setSingleLine(true);
    content.addView(radiusLabel, layout(12));
    radius = slider(0, 48, 20);
    radius.setContentDescription("Радиус скругления");
    content = page;
    content.addView(ui.section("Дополнительно"));
    group = ui.group();
    battery = new MaterialSwitch(this);
    battery.setText("Строка батареи");
    battery.setTextAppearance(
        com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
    battery.setMinHeight(dp(72));
    battery.setPadding(dp(20), dp(12), dp(20), dp(12));
    group.addView(battery, new LinearLayout.LayoutParams(-1, -2));
    content.addView(group);
  }

  private void buildModule() {
    content = screen(3);
    content.addView(ui.section("Состояние"));
    LinearLayout group = ui.group();
    group.setPadding(dp(20), dp(20), dp(20), dp(20));
    status = text("", 14);
    group.addView(status);
    content.addView(group);
    restart = button("Перезапустить SystemUI");
    ((MaterialButton) restart).setIconResource(R.drawable.ic_refresh);
    content.addView(ui.section("Сброс"));
    reset = button("Сбросить");
    ((MaterialButton) reset).setIconResource(R.drawable.ic_reset);
    content.addView(text("Сбрасывается оформление. Раскладка плиток сохраняется.", 14), layout(8));
    content.addView(ui.section("Версия"));
    content.addView(text(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME, 16));
    content.addView(text("Nothing SystemUI 16 · Android 16", 14), layout(8));
    content.addView(ui.section(getString(R.string.updates_heading)));
    openRelease = button("");
    openRelease.setVisibility(View.GONE);
    openRelease.setOnClickListener(v -> openUpdateRelease());
    checkUpdates = button(getString(R.string.updates_check));
    checkUpdates.setOnClickListener(v -> updates.check(true));
    updateStatus = text("", 14);
    updateStatus.setVisibility(View.GONE);
    updateStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    content.addView(updateStatus, layout(8));
    MaterialSwitch automatic = new MaterialSwitch(this);
    automatic.setText(R.string.updates_auto);
    automatic.setMinHeight(dp(48));
    automatic.setChecked(updates.isAutomatic());
    automatic.setOnCheckedChangeListener((button, enabled) -> updates.setAutomatic(enabled));
    content.addView(automatic, layout(8));
  }

  private void refreshUpdateControls() {
    String tag = updates.availableTag();
    openRelease.setVisibility(tag == null ? View.GONE : View.VISIBLE);
    homeUpdate.setVisibility(openRelease.getVisibility());
    if (tag != null) {
      String label = getString(R.string.updates_available, tag);
      openRelease.setText(label);
      homeUpdate.setText(label);
    }
    checkUpdates.setEnabled(!updates.isChecking());

    if (updates.isChecking()) updateStatus.setText(R.string.updates_checking);
    else if (updates.hasManualFailure()) updateStatus.setText(R.string.updates_failed);
    else if (tag == null && updates.hasChecked()) updateStatus.setText(R.string.updates_current);
    else updateStatus.setText("");
    updateStatus.setVisibility(updateStatus.length() == 0 ? View.GONE : View.VISIBLE);
  }

  private void openUpdateRelease() {
    String tag = updates.availableTag();
    if (tag == null) return;
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GitHubUpdates.releaseUrl(tag)));
    intent.addCategory(Intent.CATEGORY_BROWSABLE);
    try {
      startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.updates_open_failed, Toast.LENGTH_SHORT).show();
    }
  }

  private void inspectTile(QsLayout.Item item) {
    if (item == null || !item.editable() || committing) return;
    if (tileSheet != null) {
      pendingTileInspection = item.id();
      tileSheet.dismiss();
      return;
    }
    openTileProperties();
  }

  private void openTileProperties() {
    if (selectedItem == null || committing || propertiesSheet != null) return;
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    propertiesSheet = sheet;
    editorContent.removeView(selectionControls);
    selectionControls.setVisibility(View.VISIBLE);
    selectionControls.setPadding(dp(24), dp(16), dp(24), dp(24));
    LinearLayout panel = ui.column();
    panel.addView(
        new com.google.android.material.bottomsheet.BottomSheetDragHandleView(this),
        new LinearLayout.LayoutParams(-1, dp(40)));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(selectionControls);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    MaterialButton done = ui.button("Готово", true);
    done.setOnClickListener(v -> sheet.dismiss());
    LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-1, dp(48));
    action.setMargins(dp(24), dp(8), dp(24), dp(16));
    panel.addView(done, action);
    sheet.setContentView(panel);
    sheet.setOnDismissListener(
        v -> {
          ((android.view.ViewGroup) selectionControls.getParent()).removeView(selectionControls);
          selectionControls.setVisibility(View.GONE);
          editorContent.addView(selectionControls);
          propertiesSheet = null;
          trackingAngle = false;
          preview.endEdit();
          if (trackingControl) finishControlTracking();
        });
    sheet.show();
    sheet.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    int height = getResources().getDisplayMetrics().heightPixels;
    var behavior = sheet.getBehavior();
    behavior.setFitToContents(false);
    behavior.setMaxHeight((int) (height * .84f));
    behavior.setExpandedOffset((int) (height * .16f));
    behavior.setHalfExpandedRatio(.42f);
    behavior.setState(
        com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED);
  }

  private void openProfiles() {
    if (committing) return;
    profileSheet =
        new ProfileSheet(
            this,
            new ProfileSheet.Actions() {
              public LayoutProfile current(String name) {
                QsLayout layout = preview.getLayoutDraft();
                if (layout != null && !layout.portrait)
                  throw new IllegalArgumentException(
                      "Для сохранения раскладки поверните телефон вертикально.");
                return new LayoutProfile(name, draft(0), layout);
              }

              public void load(LayoutProfile profile) {
                applyProfile(profile);
              }

              public void export(LayoutProfile profile) {
                pendingExport = profile.json();
                exportProfile.launch(profile.name() + ".essential-tiling.json");
              }

              public void importFile() {
                importProfile.launch(new String[] {"application/json", "text/plain"});
              }
            });
    profileSheet.show();
  }

  private void applyProfile(LayoutProfile profile) {
    if (committing) throw new IllegalArgumentException("Дождитесь сохранения.");
    QsLayout next = profile.rebase(nativeSource);
    if (next != null) preview.useDraft(next);
    bind(profile.config());
    message.setText("Профиль загружен в предпросмотр. Нажмите «Применить».");
    showScreen(1);
  }

  private void writeProfile(android.net.Uri uri) {
    String data = pendingExport;
    pendingExport = "";
    PROFILE_IO.execute(
        () -> {
          String result;
          try {
            if (data.isEmpty()) throw new java.io.IOException();
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
              if (output == null) throw new java.io.IOException();
              output.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            result = "Профиль экспортирован.";
          } catch (java.io.IOException | RuntimeException e) {
            result = "Не удалось записать файл профиля.";
          }
          String text = result;
          handler.post(
              () -> {
                if (!isDestroyed()) message.setText(text);
              });
        });
  }

  private void readProfile(android.net.Uri uri) {
    PROFILE_IO.execute(
        () -> {
          LayoutProfile profile = null;
          String error = "";
          try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException();
            byte[] data = input.readNBytes(LayoutProfile.MAX_BYTES + 1);
            if (data.length > LayoutProfile.MAX_BYTES)
              throw new IllegalArgumentException("Файл профиля слишком большой.");
            profile =
                LayoutProfile.parse(new String(data, java.nio.charset.StandardCharsets.UTF_8));
          } catch (java.io.IOException | RuntimeException e) {
            error = e.getMessage() == null ? "Не удалось прочитать профиль." : e.getMessage();
          }
          LayoutProfile loaded = profile;
          String problem = error;
          handler.post(
              () -> {
                if (isDestroyed()) return;
                if (loaded == null) {
                  message.setText(problem);
                  return;
                }
                try {
                  String name = loaded.name();
                  int number = 2;
                  java.util.Set<String> names = new java.util.HashSet<>();
                  for (LayoutProfile p : ProfileStore.read(this)) names.add(p.name());
                  while (names.contains(name)) {
                    String suffix = " (" + (number++) + ")";
                    name =
                        loaded
                                .name()
                                .substring(
                                    0, Math.min(loaded.name().length(), 48 - suffix.length()))
                            + suffix;
                  }
                  if (!ProfileStore.save(
                      this, new LayoutProfile(name, loaded.config(), loaded.layout())))
                    throw new IllegalStateException("Не удалось сохранить профиль.");
                  message.setText("Профиль импортирован.");
                  openProfiles();
                } catch (RuntimeException e) {
                  message.setText(e.getMessage());
                }
              });
        });
  }

  private void bind(Config c) {
    bindingControls = true;
    enabled.setChecked(c.enabled);
    updateMasterSwitch();
    battery.setChecked(c.battery);
    size.setValue(c.scalePercent);
    gap.setValue(c.gapDp);
    shape.check(c.radiusDp < 0 ? R.id.shape_stock : R.id.shape_custom);
    radius.setValue(c.radiusDp < 0 ? 20 : c.radiusDp);
    updateRadiusVisibility();
    bindingControls = false;
    updatePreview();
  }

  private void updatePreview() {
    if (controlsReady
        && !bindingControls
        && preview != null
        && enabled != null
        && battery != null
        && size != null
        && gap != null
        && shape != null
        && radius != null) {
      Config c = draft(0);
      preview.setConfig(c);
      if (trackingControl) previewDirty = true;
      else if (previewClient != null) previewClient.setConfig(c);
      updateDraftState();
    }
  }

  private void previewUi(Runnable update) {
    if (changingTiles) return;
    if (trackingControl) pendingPreviewUi = update;
    else update.run();
  }

  private void showTilePicker(long page, int x, int y) {
    if (tilePicker != null) {
      tilePicker.target(page, x, y);
      return;
    }
    if (committing) return;
    TilePickerDialog sheet = new TilePickerDialog(this);
    android.graphics.RectF start = screenBounds(preview);
    tileSheet = sheet;
    // Reuse the editor itself: native layers, selection, gestures and minus remain identical.
    int toolbarIndex = content.indexOfChild(previewToolbar);
    android.view.ViewGroup previewParent = (android.view.ViewGroup) preview.getParent();
    int previewIndex = previewParent.indexOfChild(preview);
    android.view.ViewGroup.LayoutParams toolbarParams = previewToolbar.getLayoutParams(),
        previewParams = preview.getLayoutParams();
    View toolbarSpace = PreviewHost.snapshot(previewToolbar);
    android.widget.LinearLayout.LayoutParams spaceParams =
        new android.widget.LinearLayout.LayoutParams(
            (android.widget.LinearLayout.LayoutParams) toolbarParams);
    spaceParams.height = previewToolbar.getHeight();
    content.removeView(previewToolbar);
    content.addView(toolbarSpace, toolbarIndex, spaceParams);
    previewHost.hold(preview);
    TilePickerView picker =
        new TilePickerView(
            this,
            preview,
            previewToolbar,
            () -> selectedPage,
            sheet::dismiss,
            (entry, cell) -> tileOperation("add", entry.spec(), cell.page(), cell.x(), cell.y()));
    tilePicker = picker;
    picker.target(page, x, y);
    picker.restart(() -> saveAndMaybeRestart(true));
    sheet.transition(picker, previewHost::screenBounds);
    picker.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsets.Type.systemBars()
                      | WindowInsets.Type.displayCutout()
                      | WindowInsets.Type.ime());
          view.setPadding(dp(16) + bars.left, bars.top, dp(16) + bars.right, dp(8) + bars.bottom);
          return insets;
        });
    sheet.setOnDismissListener(
        dialog -> {
          if (tileSheet == sheet) {
            tileSheet = null;
            tilePicker = null;
          }
          ((android.view.ViewGroup) previewToolbar.getParent()).removeView(previewToolbar);
          ((android.view.ViewGroup) preview.getParent()).removeView(preview);
          preview.setCatalogueProgress(0);
          preview.setInsertionCell(-1, -1, -1);
          previewHost.releaseFrame();
          content.removeView(toolbarSpace);
          ((android.widget.ImageView) toolbarSpace).setImageDrawable(null);
          content.addView(previewToolbar, toolbarIndex, toolbarParams);
          previewParent.addView(preview, previewIndex, previewParams);
          if (catalogLoading) {
            catalogLoading = false;
            catalogGeneration++;
            tileClient.cancelCatalog();
          }
          // The inspector must wait until the shared grid and toolbar are back in the editor.
          String inspect = pendingTileInspection;
          pendingTileInspection = null;
          if (inspect != null
              && !isFinishing()
              && !isDestroyed()
              && selectedItem != null
              && inspect.equals(selectedItem.id())) openTileProperties();
        });
    sheet.setContentView(picker);
    android.view.Window window = sheet.getWindow();
    if (window != null) {
      window.setDecorFitsSystemWindows(false);
      window.setLayout(-1, -1);
      window.setWindowAnimations(0);
      window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
      window.setSoftInputMode(
          android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
              | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    picker.animateEnter(start, previewHost.viewportBounds());
    sheet.show();
    sheet.getWindow().setLayout(-1, -1);
    picker.retry(() -> loadCatalogue(picker));
    loadCatalogue(picker);
  }

  private static android.graphics.RectF screenBounds(View view) {
    int[] xy = new int[2];
    view.getLocationOnScreen(xy);
    return new android.graphics.RectF(
        xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
  }

  private void loadCatalogue(TilePickerView picker) {
    if (committing || tilePicker != picker || catalogLoading) return;
    catalogLoading = true;
    int token = ++catalogGeneration;
    picker.loading();
    Bundle request = new Bundle();
    request.putString("action", "catalog");
    tileClient.request(
        request,
        (entries, error) -> {
          if (isDestroyed() || token != catalogGeneration) return;
          catalogLoading = false;
          if (tilePicker != picker) return;
          if (!error.isEmpty()) {
            picker.catalogError(error);
            return;
          }
          picker.entries(entries);
        });
  }

  private void tileOperation(String action, String spec, long page, int x, int y) {
    if (committing) return;
    if (catalogLoading) {
      catalogLoading = false;
      catalogGeneration++;
      tileClient.cancelCatalog();
      if (tilePicker != null) tilePicker.catalogError("Каталог не загружен.");
    }
    QsLayout current = preview.getLayoutDraft();
    if (current == null) return;
    String pending = layoutDraft;
    Bundle request = new Bundle();
    request.putString("action", action);
    request.putString("spec", spec);
    // Remove carries the draft in one request; no intermediate refresh or future fingerprint is
    // guessed.
    request.putString(
        "base",
        action.equals("remove") || pending.isEmpty()
            ? current.base
            : QsLayout.parse(pending).asSource().base);
    if (action.equals("remove") && !pending.isEmpty()) request.putString("layout", pending);
    request.putLong("page", page);
    request.putInt("x", x);
    request.putInt("y", y);
    changingTiles = true;
    previewClient.beginMutation();
    setTileStatus(action.equals("remove") ? "Удаление плитки…" : "Сохранение…");
    setPreviewBusy(false);
    setCommitting(true);
    message.setText(action.equals("remove") ? "Удаление плитки…" : "Сохранение…");
    if (action.equals("remove")) preview.pendingRemoval("t:" + spec);
    Runnable send =
        () ->
            tileClient.request(
                request,
                (entries, error) -> {
                  if (isDestroyed()) return;
                  changingTiles = false;
                  setCommitting(false);
                  setPreviewBusy(false);
                  preview.pendingRemoval(null);
                  if (error.isEmpty()) {
                    int newPage = previewPages;
                    layoutDraft = "";
                    nativeSource = null;
                    discard.setVisibility(View.INVISIBLE);
                    message.setText("");
                    setTileStatus("");
                    if (action.equals("page")) {
                      selectedPage = newPage;
                      previewClient.setPage(newPage);
                    }
                    if (tilePicker != null) tilePicker.waitingForFrame();
                    previewClient.endMutation("");
                    if (tilePicker != null && tilePicker.needsLoad()) loadCatalogue(tilePicker);
                  } else mutationFailed(error);
                });
    if (pending.isEmpty() || action.equals("remove")) send.run();
    else
      layoutClient.save(
          pending,
          (success, error) -> {
            if (isDestroyed()) return;
            if (!success) {
              changingTiles = false;
              setCommitting(false);
              setPreviewBusy(false);
              preview.pendingRemoval(null);
              mutationFailed(error);
              return;
            }
            layoutDraft = "";
            discard.setVisibility(View.INVISIBLE);
            nativeSource = null;
            send.run();
          });
  }

  private void mutationFailed(String error) {
    message.setText(error);
    setTileStatus(error);
    if (tilePicker != null) tilePicker.frameReady();
    previewClient.endMutation(layoutDraft);
  }

  private void setPreviewStatus(String text) {
    previewStatus.setText(text);
    previewStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void setTileStatus(String text) {
    tileActionStatus.setText(text);
    tileActionStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void selectPage(int page) {
    selectedPage = Math.max(0, Math.min(page, previewPages - 1));
    previewPage.setText((selectedPage + 1) + " / " + previewPages);
    updateNavigation();
    previewClient.setPage(selectedPage);
  }

  private void discardLayout() {
    layoutDraft = "";
    nativeSource = null;
    DraftStore.clear(this);
    discard.setVisibility(View.INVISIBLE);
    preview.clearLocalDraft();
    previewClient.setLayout("");
    previewClient.refresh();
  }

  private void updateSelection() {
    if (angle != null) {
      bindingAngle = true;
      angle.setEnabled(selectedItem != null && !committing && !previewBusy);
      if (selectedItem != null && !trackingAngle) {
        float value = Math.round(selectedItem.form().rotation());
        if (value == -180 && angle.getValue() > 0) value = 180;
        angle.setValue(value);
      }
      bindingAngle = false;
    }
    boolean editable =
        selectedItem != null
            && selectedItem.editable()
            && preview.getLayoutDraft() != null
            && preview.getLayoutDraft().portrait;
    propertiesButton.setEnabled(editable && !committing && !previewBusy);
    selectedTitle.setText(selectedItem == null ? "Выберите плитку" : selectedItem.title());
    if (selectedItem == null && propertiesSheet != null) propertiesSheet.dismiss();
    if (selectedItem == null) return;
    selectionLabel.setText(
        selectedItem.title()
            + " · "
            + selectedItem.form().columns(selectedItem.w())
            + "×"
            + selectedItem.form().rows(selectedItem.h())
            + (selectedItem.form().cut() ? " · " + selectedItem.cellCount() + " кл." : "")
            + (editable ? "" : " · штатный виджет"));
    setChildrenEnabled(selectionControls, editable && !previewBusy && !committing);
    largeTile.setEnabled(editable && !previewBusy && !committing);
  }

  private void setPreviewBusy(boolean value) {
    previewBusy = value;
    previewProgress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
    preview.setBusy(value || committing);
    updateHistory();
    updateSelection();
  }

  private void setCommitting(boolean value) {
    committing = value;
    updateHistory();
    if (restartAction != null) restartAction.setEnabled(!value);
    if (tilePicker != null) tilePicker.busy(value);
    for (View v :
        new View[] {enabled, battery, size, gap, shape, radius, apply, reset, restart, discard})
      v.setEnabled(!value);
    updateNavigation();
    preview.setBusy(value || previewBusy);
    updateSelection();
    updateDraftState();
  }

  private boolean canAppendPage() {
    QsLayout model = preview.getLayoutDraft();
    if (model == null || !model.portrait || model.pages.isEmpty()) return false;
    long last = model.pages.get(model.pages.size() - 1).id();
    for (QsLayout.Item item : model.items) if (item.page() == last) return true;
    return false;
  }

  private void updateNavigation() {
    previousPage.setEnabled(!committing && selectedPage > 0);
    nextPage.setEnabled(!committing && (selectedPage + 1 < previewPages || canAppendPage()));
  }

  private void setChildrenEnabled(android.view.ViewGroup group, boolean enabled) {
    for (int n = 0; n < group.getChildCount(); n++) {
      View child = group.getChildAt(n);
      child.setEnabled(enabled);
      if (child instanceof android.view.ViewGroup)
        setChildrenEnabled((android.view.ViewGroup) child, enabled);
    }
  }

  private void updateHistory() {
    if (undo != null) undo.setEnabled(!committing && !previewBusy && preview.canUndo());
    if (redo != null) redo.setEnabled(!committing && !previewBusy && preview.canRedo());
  }

  private void updateRadiusVisibility() {
    boolean custom = shape.getCheckedButtonId() == R.id.shape_custom;
    radius.setVisibility(custom ? View.VISIBLE : View.GONE);
    radiusLabel.setVisibility(custom ? View.VISIBLE : View.GONE);
  }

  private Config draft(long revision) {
    return new Config(
        enabled.isChecked(),
        Math.round(size.getValue()),
        Math.round(gap.getValue()),
        shape.getCheckedButtonId() == R.id.shape_stock ? -1 : Math.round(radius.getValue()),
        battery.isChecked(),
        revision);
  }

  private boolean hasChanges() {
    return controlsReady
        && (!layoutDraft.isEmpty() || !draft(0).sameValues(SettingsStore.read(this)));
  }

  private boolean needsRestart() {
    SharedPreferences runtime =
        createDeviceProtectedStorageContext().getSharedPreferences("runtime", Context.MODE_PRIVATE);
    long revision = SettingsStore.read(this).revision;
    return (runtime.getLong("time", 0) > 0 || revision > 0)
        && runtime.getLong("loadedRevision", -1) != revision;
  }

  private void updateDraftState() {
    if (!controlsReady || bindingControls) return;
    boolean dirty = hasChanges(), pending = needsRestart();
    apply.setEnabled(!committing && (dirty || pending));
    if (revertAction != null) revertAction.setEnabled(!committing && dirty);
    discard.setEnabled(!committing);
    discard.setVisibility(dirty ? View.VISIBLE : View.INVISIBLE);
    // Keep every visible label fixed while the finger is on a slider.
    if (trackingControl) return;
    String hint =
        dirty
            ? "Есть несохранённые изменения"
            : pending ? "Оформление сохранено. Нужен перезапуск SystemUI." : "Изменений нет";
    String current = message.getText().toString();
    if (current.isEmpty() || current.equals(draftHint)) message.setText(hint);
    draftHint = hint;
    String title = !dirty && pending ? "Перезапустить" : "Применить";
    apply.setContentDescription(
        !dirty && pending ? "Перезапустить SystemUI" : "Применить изменения");
    if (!title.contentEquals(apply.getText())) apply.setText(title);
    apply.setStateDescription(
        dirty ? "Есть изменения" : pending ? "Ожидается перезапуск" : "Изменений нет");
  }

  private void revertDraft() {
    if (committing) return;
    if (propertiesSheet != null) propertiesSheet.dismiss();
    boolean restored = false;
    if (nativeSource != null && !previewBusy)
      try {
        preview.useDraft(nativeSource);
        layoutDraft = "";
        previewClient.setLayout("");
        preview.clearLocalDraft();
        restored = true;
      } catch (IllegalArgumentException ignored) {
      }
    if (!restored) discardLayout();
    DraftStore.clear(this);
    message.setText("");
    bind(SettingsStore.read(this));
  }

  @Override
  public boolean onKeyShortcut(int keyCode, android.view.KeyEvent event) {
    if (screen == 1 && event.isCtrlPressed() && !event.isAltPressed()) {
      if (keyCode == android.view.KeyEvent.KEYCODE_Z) {
        if (event.isShiftPressed()) preview.redo();
        else preview.undo();
        return true;
      }
      if (keyCode == android.view.KeyEvent.KEYCODE_Y) {
        preview.redo();
        return true;
      }
    }
    return super.onKeyShortcut(keyCode, event);
  }

  private boolean saveDraft() {
    Config previous = SettingsStore.read(this);
    Config next = draft(previous.revision);
    boolean changed = !previous.sameValues(next);
    if (changed) next = draft(Math.max(System.currentTimeMillis(), previous.revision + 1));
    if (changed && !SettingsStore.write(this, next)) {
      message.setText("Не удалось сохранить настройки.");
      return false;
    }
    message.setText("");
    refreshStatus();
    return true;
  }

  private void saveAndMaybeRestart(boolean restartAfter) {
    if (committing) return;
    if (layoutDraft.isEmpty()) {
      if (!saveDraft()) return;
      if (restartAfter) restartSystemUi();
      return;
    }
    setCommitting(true);
    message.setText("Сохранение раскладки в SystemUI…");
    layoutClient.save(
        layoutDraft,
        (success, error) -> {
          if (isDestroyed()) return;
          setCommitting(false);
          if (!success) {
            message.setText(error);
            return;
          }
          discardLayout();
          if (!saveDraft()) return;
          if (restartAfter) restartSystemUi();
          else
            message.setText(
                needsRestart()
                    ? "Раскладка сохранена. Для оформления нужен перезапуск SystemUI."
                    : "Раскладка сохранена.");
        });
  }

  private void restartSystemUi() {
    setCommitting(true);
    message.setText("Запрос модулю. Ожидаю новый запуск SystemUI…");
    restartClient.start();
  }

  private void refreshStatus() {
    if (trackingControl) return;
    updateDraftState();
    SharedPreferences p =
        createDeviceProtectedStorageContext().getSharedPreferences("runtime", Context.MODE_PRIVATE);
    long time = p.getLong("time", 0);
    if (time == 0) {
      status.setText(
          "Нет отчёта SystemUI. Включите Essential Tiling в LSPosed, "
              + "выберите SystemUI и перезапустите SystemUI.");
      return;
    }
    boolean applied = p.getLong("loadedRevision", -1) == SettingsStore.read(this).revision;
    String date =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time));
    status.setText(
        (applied ? "Сохранённые настройки загружены" : "Ожидается перезапуск SystemUI")
            + "\nПоследний отчёт · "
            + date
            + "\n"
            + p.getString("summary", ""));
  }

  @Override
  protected void onResume() {
    super.onResume();
    updates.observe(refreshUpdates);
    updates.check(false);
    updatePreview();
    handler.post(refresh);
    previewClient.start();
  }

  @Override
  protected void onPause() {
    updates.forget(refreshUpdates);
    if (controlsReady) DraftStore.write(this, draft(0), layoutDraft);
    preview.endEdit();
    trackingControl = false;
    previewDirty = false;
    pendingPreviewUi = null;
    previewClient.stop();
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    pendingTileInspection = null;
    if (propertiesSheet != null) propertiesSheet.dismiss();
    if (tileSheet instanceof TilePickerDialog dialog) dialog.dismissImmediately();
    else if (tileSheet != null) tileSheet.dismiss();
    if (profileSheet != null) profileSheet.dismiss();
    restartClient.stop();
    layoutClient.stop();
    tileClient.stop();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    Config c = draft(0);
    out.putBoolean("enabled", c.enabled);
    out.putInt("scale", c.scalePercent);
    out.putInt("gap", c.gapDp);
    out.putInt("radius", c.radiusDp);
    out.putBoolean("battery", c.battery);
    out.putInt("preview_page", selectedPage);
    out.putInt("screen", screen);
    out.putString("layout_draft", layoutDraft);
    out.putString("profile_export", pendingExport);
    out.putString("edit_history", preview.historySnapshot());
    super.onSaveInstanceState(out);
  }

  private void finishControlTracking() {
    trackingControl = false;
    Runnable pending = pendingPreviewUi;
    pendingPreviewUi = null;
    if (previewDirty) {
      previewDirty = false;
      updatePreview();
    } else if (pending != null) pending.run();
    refreshStatus();
  }

  private LinearLayout.LayoutParams navigationParams() {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
    p.setMargins(dp(4), 0, dp(4), 0);
    return p;
  }

  private Slider slider(int min, int max, int initial) {
    Slider v = sliderControl(min, max, initial);
    content.addView(v, layout(4));
    return v;
  }

  private Slider sliderControl(int min, int max, int initial) {
    Slider v = new Slider(this);
    v.setValueFrom(min);
    v.setValueTo(max);
    v.setStepSize(1);
    v.setValue(initial);
    v.setLabelBehavior(LabelFormatter.LABEL_GONE);
    v.setMinimumHeight(dp(56));
    v.addOnChangeListener(
        (bar, value, fromUser) -> {
          bar.setStateDescription(
              bar == size ? Math.round(value) + "%" : Math.round(value) + " dp");
          updatePreview();
        });
    v.addOnSliderTouchListener(
        new Slider.OnSliderTouchListener() {
          @Override
          public void onStartTrackingTouch(Slider bar) {
            trackingControl = true;
          }

          @Override
          public void onStopTrackingTouch(Slider bar) {
            finishControlTracking();
          }
        });
    return v;
  }

  private Button button(String label) {
    Button view = ui.button(label, false);
    palette.button(view, false);
    content.addView(view, layout(6));
    return view;
  }

  private TextView text(String value, int sp) {
    TextView view =
        ui.text(
            value,
            sp >= 16
                ? com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
                : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    view.setTextColor(sp <= 14 ? palette.secondary : palette.text);
    return view;
  }

  private Button compactButton(
      LinearLayout row, String label, String description, Runnable action) {
    Button view = ui.button(label, false);
    view.setTextSize(14);
    view.setContentDescription(description);
    palette.button(view, false);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(dp(2), 0, dp(2), 0);
    row.addView(view, lp);
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private LinearLayout.LayoutParams layout(int top) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(top);
    return p;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
