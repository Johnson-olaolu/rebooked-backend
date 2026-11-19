import { Tab, TabList } from "@fluentui/react-components";
import { getPreviewDataState, setPreviewDataState } from "../api/previewState";
import type { PreviewDataState } from "../api/previewState";

const states: { value: PreviewDataState; label: string }[] = [
  { value: "data", label: "Data" },
  { value: "loading", label: "Loading" },
  { value: "empty", label: "Empty" },
  { value: "error", label: "Error" },
];

export default function PreviewStateSwitcher() {
  if (!import.meta.env.DEV) return null;
  const selectedState = getPreviewDataState();
  return (
    <div className="preview-state-switcher" aria-label="Preview data state">
      <span>Preview</span>
      <TabList
        size="small"
        selectedValue={selectedState}
        onTabSelect={(_, data) => {
          const nextState = states.find((state) => state.value === data.value)?.value;
          if (nextState) setPreviewDataState(nextState);
        }}
      >
        {states.map((state) => <Tab key={state.value} value={state.value}>{state.label}</Tab>)}
      </TabList>
    </div>
  );
}