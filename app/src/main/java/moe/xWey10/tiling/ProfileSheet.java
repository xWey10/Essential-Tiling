package moe.xWey10.tiling;

import android.content.Context;
import android.view.*;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Small local profile library. Loading only changes the draft, never writes to SystemUI. */
final class ProfileSheet {
  interface Actions {
    LayoutProfile current(String name);

    void load(LayoutProfile profile);

    void export(LayoutProfile profile);

    void importFile();
  }

  private final Context context;
  private final SettingsUi ui;
  private final Actions actions;
  private final BottomSheetDialog dialog;
  private final LinearLayout list;

  ProfileSheet(Context c, Actions actions) {
    context = c;
    this.actions = actions;
    ui = new SettingsUi(c);
    dialog = new BottomSheetDialog(c);
    LinearLayout panel = ui.column();
    panel.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(24));
    panel.addView(ui.heading("Профили"));
    LinearLayout bar = new LinearLayout(c);
    var save = ui.button("Сохранить", true);
    save.setOnClickListener(v -> save());
    var load = ui.button("Импорт", false);
    load.setOnClickListener(
        v -> {
          dialog.dismiss();
          actions.importFile();
        });
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ui.dp(48), 1);
    left.setMarginEnd(ui.dp(8));
    bar.addView(save, left);
    bar.addView(load, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
    panel.addView(bar);
    ScrollView scroll = new ScrollView(c);
    list = ui.column();
    list.setPadding(0, ui.dp(12), 0, 0);
    scroll.addView(list);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    dialog.setContentView(panel);
    render();
  }

  void show() {
    dialog.show();
    dialog
        .getBehavior()
        .setMaxHeight((int) (context.getResources().getDisplayMetrics().heightPixels * .8f));
    dialog
        .getBehavior()
        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
  }

  void dismiss() {
    dialog.dismiss();
  }

  private void render() {
    list.removeAllViews();
    var profiles = ProfileStore.read(context);
    if (profiles.isEmpty()) {
      TextView empty =
          ui.text(
              "Нет сохранённых профилей",
              com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
      empty.setPadding(ui.dp(8), ui.dp(24), ui.dp(8), ui.dp(24));
      list.addView(empty);
      return;
    }
    for (LayoutProfile profile : profiles) {
      LinearLayout row = new LinearLayout(context);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setBackground(ui.surface(new ThemePalette(context).surface, 24));
      String description =
          profile.layout() == null
              ? "Оформление"
              : "Оформление · плиток: " + profile.layout().items.size();
      row.addView(
          ui.row(profile.name(), description, R.drawable.ic_profiles, () -> load(profile)),
          new LinearLayout.LayoutParams(0, -2, 1));
      var more = ui.button("⋮", false);
      more.setContentDescription("Действия: " + profile.name());
      more.setOnClickListener(
          v -> {
            PopupMenu menu = new PopupMenu(context, v);
            menu.getMenu().add(0, 1, 0, "Загрузить");
            menu.getMenu().add(0, 2, 0, "Экспорт");
            menu.getMenu().add(0, 3, 0, "Удалить");
            menu.setOnMenuItemClickListener(
                item -> {
                  if (item.getItemId() == 1) load(profile);
                  else if (item.getItemId() == 2) {
                    dialog.dismiss();
                    actions.export(profile);
                  } else if (ProfileStore.delete(context, profile.name())) {
                    render();
                    Snackbar.make(list, "Профиль удалён", Snackbar.LENGTH_LONG)
                        .setAction(
                            "Отменить",
                            a -> {
                              ProfileStore.save(context, profile);
                              render();
                            })
                        .show();
                  }
                  return true;
                });
            menu.show();
          });
      LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48));
      mp.setMarginEnd(ui.dp(8));
      row.addView(more, mp);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
      rp.bottomMargin = ui.dp(8);
      list.addView(row, rp);
    }
  }

  private void load(LayoutProfile profile) {
    try {
      actions.load(profile);
      dialog.dismiss();
    } catch (RuntimeException e) {
      Snackbar.make(
              list,
              e.getMessage() == null ? "Не удалось загрузить профиль." : e.getMessage(),
              Snackbar.LENGTH_LONG)
          .show();
    }
  }

  private String defaultName() {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (LayoutProfile p : ProfileStore.read(context)) names.add(p.name());
    for (int n = 1; ; n++) {
      String name = context.getString(R.string.profile_default_name, n);
      if (!names.contains(name)) return name;
    }
  }

  private void save() {
    TextInputLayout field = new TextInputLayout(context);
    field.setHint("Название");
    TextInputEditText name = new TextInputEditText(field.getContext());
    name.setSingleLine(true);
    name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(48)});
    name.setText(defaultName());
    field.addView(name, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout panel = ui.column();
    panel.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), 0);
    panel.addView(field);
    var prompt =
        new MaterialAlertDialogBuilder(context)
            .setTitle("Сохранить профиль")
            .setView(panel)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create();
    prompt.setOnShowListener(
        v ->
            prompt
                .getButton(android.app.Dialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    button -> {
                      try {
                        String title =
                            name.getText() == null ? "" : name.getText().toString().trim();
                        for (LayoutProfile p : ProfileStore.read(context))
                          if (p.name().equals(title))
                            throw new IllegalArgumentException("Такое название уже есть.");
                        if (!ProfileStore.save(context, actions.current(title)))
                          throw new IllegalStateException("Не удалось сохранить профиль.");
                        prompt.dismiss();
                        render();
                      } catch (RuntimeException e) {
                        field.setError(e.getMessage());
                      }
                    }));
    prompt.show();
  }
}
