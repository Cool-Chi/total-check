---
name: advanced-interaction-design
description: Identify the appropriate interaction pattern from 10 advanced interaction models based on app or mini-program interface requirements, and generate ready-to-use English implementation prompts for AI.
---

# Advanced Interaction Design

## Purpose

When a user describes an interface interaction for an app or mini-program, first determine which interaction pattern truly fits the requirement, then organize the requirement into an implementation prompt that can be given directly to an AI.

This is not a video production skill, nor is it for having AI randomly add animations to an interface. The focus is on determining:

- Where the change originates;
- Where the user's action ultimately lands;
- Which elements need to respond together;
- Whether surrounding content needs to actively make room;
- What result the user should see after the animation finishes.

## Usage Rules

1. First understand the task the user wants to accomplish before choosing an interaction type. Do not simply add a generic Fade In/Out just because the word "animation" is mentioned.
2. Select one best-matching primary interaction by default. If a requirement genuinely involves multiple interactions, split them into a "Primary Interaction" and a "Secondary Interaction," and explain the relationship between the two.
3. Preserve the user's existing visual style, component structure, and tech stack. Do not rewrite unrelated pages.
4. When describing an interaction, clearly specify: the trigger mechanism, start state, transition process, end state, and failed or canceled state.
5. Output code based on the user's specified tech stack only when code implementation is requested. When the user only asks for a prompt, do not output full project code unprompted.
6. All `Copyable prompt` sections must be written in English so the user can copy and paste them directly to other AIs. Do not mix other languages into the English prompt.
7. Do not treat vague terms like "more premium," "better texture," or "smoother" as complete requirements. Translate them into concrete state changes, position changes, size changes, or feedback mechanisms.
8. If key information is missing but can still be reasonably inferred, state a clear assumption and proceed rather than asking repeated follow-up questions.

## Evaluation Method

First, pinpoint the requirement using the four questions below:

| Evaluation Question | Key Interactions to Consider |
| :--- | :--- |
| Where should the change originate? | Radial Theme Transition, Drag-to-Reorder |
| Where should the action ultimately land? | Staggered Bulk Selection, Velocity-Based Slider Snap |
| Which elements need to respond together? | Spring Stepper Progress, Ripple Feedback for Related Switches |
| Should surrounding content make room? | Curved Card Deletion, Stacked Card Scroll, Expanding Tag Selection |

If the content itself changes from hidden to visible, prioritize checking `Animated Text Disclosure`.

## 10 Interaction Patterns

### 1. Radial Theme Transition

**Use case:** The user clicks a theme toggle button to switch from light mode to dark mode, or switches between two complete views.

**Must preserve:** Use the actual click or touch position as the center of the circle; the circle's radius must cover the farthest screen corner; both views maintain the exact same size and position; clip only the top layer without scaling the page content.

```text
Implement a radial theme transition for an app interface. When the user presses the theme toggle, reveal the new theme from the exact pointer or touch position as the center of an expanding circle. Calculate the circle radius based on the distance from the touch point to the farthest viewport corner. Keep both theme layers at the same scale and position, and clip only the top layer with a circular mask instead of scaling the page. Support mouse and touch input, respect prefers-reduced-motion, and use a simple fallback transition when needed.

```

### 2. Drag-to-Reorder

**Use case:** The user drags list items, cards, or task items to rearrange their order.

**Must preserve:** The dragged item temporarily leaves the normal list flow; the drop index is recalculated on every frame; other items form a clear empty slot; each item independently catches up to its new position; if the user pauses mid-drag, the layout stays in its current state.

```text
Create a draggable sortable list. When the user holds and drags one row, temporarily remove it from the normal list flow and calculate the insertion index continuously from the pointer position. The other rows should create a visible empty slot and move out of the way. Animate each row independently with a spring motion so the layout feels soft and staggered. Keep the dragged item attached to the pointer, prevent layout jumps, and support both touch and keyboard interactions.

```

### 3. Staggered Bulk Selection

**Use case:** The user clicks select-all or performs a bulk action, requiring multiple items to enter the selected state sequentially.

**Must preserve:** The underlying state can update immediately, while visual feedback executes in a staggered sequence starting from the first item; each checkmark appears with a subtle elastic scale effect; the animation must not block the user from continuing to interact.

```text
Implement a select-all interaction for a list of four items. When the user activates select all, update the selection state immediately but animate the checkmarks one by one with a short staggered delay from the first item to the last. Add a subtle elastic scale effect when each checkmark appears, then return each item to its normal size. Make the sequence feel deliberate without slowing down the actual state update, and support keyboard and screen-reader accessibility.

```

### 4. Velocity-Based Slider Snap

**Use case:** The user drags a stepped slider to select a value, gear, or target level.

**Must preserve:** Track the release velocity; allow the thumb to overshoot slightly past the release point after letting go; pull it back to the nearest valid tick via a spring animation; clamp the final value within the valid range.

```text
Create a stepped slider for selecting a target value. Track the pointer velocity while the user drags. When the user releases the slider, let the thumb continue slightly past the release point based on the release velocity, then use a spring animation to pull it back to the nearest valid tick. Clamp the final value to the available range, make the overshoot subtle, and support mouse, touch, keyboard control, and reduced-motion preferences.

```

### 5. Animated Text Disclosure

**Use case:** A block of descriptive text, details, or help information needs to toggle between collapsed and expanded states.

**Must preserve:** Adjust the container based on the actual content height; transition continuously from the current height to the target height; rotate the chevron or icon by 180 degrees; prevent text from popping in abruptly or causing layout jumps.

```text
Create an animated text disclosure component for a collapsible description. When the user opens it, measure the real content height and animate the container from its current height to the measured height instead of suddenly revealing the text. When it closes, animate back to the collapsed height. Rotate the trailing chevron by 180 degrees to represent the two states, keep the content accessible to screen readers, and avoid layout jumps.

```

### 6. Spring Stepper Progress

**Use case:** The user completes a step in a form, checkout, or setup flow and advances to the next step.

**Must preserve:** The progress bar slightly overshoots the target position before springing back to the exact spot; completed, current, and upcoming states must be clearly distinguished; the animation must not alter the actual step state.

```text
Create a multi-step progress indicator. When the user completes a step, animate the next progress segment so it slightly overshoots its target and then settles back with a soft spring motion. Update the completed, current, and upcoming states clearly, keep the progress value accurate throughout the animation, and make the transition feel responsive without delaying navigation. Support keyboard accessibility and prefers-reduced-motion.

```

### 7. Ripple Feedback for Related Switches

**Use case:** Within a group of related settings, the user toggles one switch and needs a visual cue that they belong to the same setting group.

**Must preserve:** The active switch changes its state normally; neighboring switches only produce a subtle shake or ripple feedback; the actual on/off states of neighboring switches must not be changed.

```text
Create a settings group with multiple toggle switches. When the user changes one switch, trigger a subtle ripple-like feedback effect that spreads to the neighboring switches. The neighboring switches may slightly shake or translate, but their actual on and off states must not change. The source switch should update normally, while the ripple remains purely visual feedback. Keep the effect contained within the group, support touch and keyboard input, and disable the motion for reduced-motion users.

```

### 8. Curved Card Deletion

**Use case:** The user swipes to delete a card, message, or task and needs a clear visual destination for the deletion action.

**Must preserve:** The card moves along a curved path toward the delete icon or trash area; it gradually scales down, rotates, and fades out while moving; if released before reaching the delete threshold, it cancels and returns to its original position; remove the item from the data source only after the animation finishes.

```text
Create a swipe-to-delete card interaction. When the user swipes a card past the delete threshold, animate the card along a curved path toward the delete icon or trash area. While moving, gradually reduce its scale, rotate it slightly, and fade its opacity. Remove the card from the data source after the exit animation completes. If the user releases before the threshold, smoothly return the card to its original position. Support touch, mouse, keyboard deletion, and reduced-motion preferences.

```

### 9. Stacked Card Scroll

**Use case:** The user scrolls through a sequence of cards, logs, or historical items and needs spatial cues for content already viewed.

**Must preserve:** The top card pins temporarily once it reaches the boundary; subsequent cards move upward and compress the preceding cards into a stack; compression level, scale, and depth adjust based on the number of cards behind; content must not disappear abruptly.

```text
Create a vertically scrollable card stack. When the top card reaches the top boundary, keep it pinned temporarily while the cards behind it move upward and compress into a visible stack. Calculate each card's vertical offset, scale, and depth based on how many cards are behind it. The more cards that move forward, the deeper and smaller the previous cards should become. Preserve the user's scroll context, avoid abrupt disappearance, and support touch, mouse wheel, and keyboard scrolling.

```

### 10. Expanding Tag Selection

**Use case:** The user selects an option from a row of tags, filters, or categories and needs the active selection to stand out.

**Must preserve:** The active tag scales up slightly; neighboring tags smoothly shift aside to make room; elements must not overlap or jump abruptly; line wrapping must be handled properly on smaller screens.

```text
Create a selectable tag list with animated layout reflow. When the user selects a tag, slightly enlarge the active tag and make the neighboring tags move aside to create enough space. The surrounding tags should smoothly translate rather than overlap or jump. Clearly show the selected state, preserve the original order of the tags, handle wrapping on smaller screens, and support mouse, touch, keyboard navigation, and reduced-motion preferences.

```

## Output Format

When a user describes a specific interface requirement, respond using the following structure:

### Selected interaction

State the name of the best-matching interaction pattern.

### Why this fits

Briefly explain in English whether it solves "where the change originates," "where the action lands," "which elements respond together," or "whether surrounding content makes room."

### Interaction behavior

Explain in English the trigger mechanism, start state, transition process, final state, canceled or failed state, and touch target considerations for mobile devices.

### Copyable prompt

Output a single English prompt block that includes:

* Specific components or interface elements;
* Trigger mechanism;
* Start state and end state;
* Concrete changes such as translation, dimensions, opacity, clipping, spring, or overshoot;
* The relationship between data state and visual state;
* Responsive, keyboard, touch, and reduced-motion requirements;
* Instruction not to rewrite unrelated components.

```text
Use the selected interaction pattern in my existing interface. Preserve the current visual style, layout language, and component structure. Do not rewrite unrelated components. Implement the trigger, state changes, start and end states, motion behavior, responsive behavior, keyboard accessibility, touch support, and reduced-motion fallback described below:

[Insert the selected English interaction prompt here]

```

## What to Avoid

* Do not simply say "add a premium animation."
* Do not mix all 10 patterns together in a single output.
* Do not misrepresent visual feedback as an actual functional state change (such as toggling neighboring switches).
* Do not use a fixed duration to mask undefined start and end states.
* Do not output video editing, voiceover, subtitles, or video production workflows.
* Do not generate bilingual two-column prompt graphics; if an image is needed, provide only the structure and copy suitable for a concise infographic.
