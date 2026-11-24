const root = {
	"docs": [],
	"modules": [],
	"packages": [
		"freja.store",
		"freja.model",
		"freja.util",
		"freja.ui",
		"freja",
		"freja.scan",
		"laevatain"
	],
	"types": [
		{
			"name": "PhotoImporter",
			"packageName": "freja.store",
			"type": "Class"
		},
		{
			"name": "PhotoImporter.AnalyzedPhoto",
			"packageName": "freja.store",
			"type": "Record"
		},
		{
			"name": "Exif",
			"packageName": "freja.store",
			"type": "Class"
		},
		{
			"name": "Exif.Data",
			"packageName": "freja.store",
			"type": "Record"
		},
		{
			"name": "Library",
			"packageName": "freja.store",
			"type": "Class"
		},
		{
			"name": "Library.PhotoSave",
			"packageName": "freja.store",
			"type": "Record"
		},
		{
			"name": "Library.LibraryException",
			"packageName": "freja.store",
			"type": "Exception"
		},
		{
			"name": "Library.FileStamp",
			"packageName": "freja.store",
			"type": "Record"
		},
		{
			"name": "Library.Thumbnail",
			"packageName": "freja.store",
			"type": "Record"
		},
		{
			"name": "LibraryStats",
			"packageName": "freja.model",
			"type": "Record"
		},
		{
			"name": "Tag",
			"packageName": "freja.model",
			"type": "Record"
		},
		{
			"name": "Photo",
			"packageName": "freja.model",
			"type": "Record"
		},
		{
			"name": "Folder",
			"packageName": "freja.model",
			"type": "Record"
		},
		{
			"name": "PhotoFilter",
			"packageName": "freja.model",
			"type": "Record"
		},
		{
			"name": "PhotoFilter.SortOrder",
			"packageName": "freja.model",
			"type": "Enum"
		},
		{
			"name": "SmartTags",
			"packageName": "freja.util",
			"type": "Class"
		},
		{
			"name": "PathUtil",
			"packageName": "freja.util",
			"type": "Class"
		},
		{
			"name": "ImageTransforms",
			"packageName": "freja.util",
			"type": "Class"
		},
		{
			"name": "PhotoGrid",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "PhotoGrid.GroupMode",
			"packageName": "freja.ui",
			"type": "Enum"
		},
		{
			"name": "Icons",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "Theme",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "Theme.Preview",
			"packageName": "freja.ui",
			"type": "Record"
		},
		{
			"name": "Theme.ThemeInfo",
			"packageName": "freja.ui",
			"type": "Record"
		},
		{
			"name": "PhotoInspector",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "PhotoInspector.Listener",
			"packageName": "freja.ui",
			"type": "Interface"
		},
		{
			"name": "Sidebar",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "Sidebar.Listener",
			"packageName": "freja.ui",
			"type": "Interface"
		},
		{
			"name": "Sidebar.Mode",
			"packageName": "freja.ui",
			"type": "Enum"
		},
		{
			"name": "ScanFoldersDialog",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "ScanFoldersDialog.Listener",
			"packageName": "freja.ui",
			"type": "Interface"
		},
		{
			"name": "PhotoViewer",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "PhotoViewer.Listener",
			"packageName": "freja.ui",
			"type": "Functional"
		},
		{
			"name": "MainWindow",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "SettingsPanel",
			"packageName": "freja.ui",
			"type": "Class"
		},
		{
			"name": "SettingsPanel.Listener",
			"packageName": "freja.ui",
			"type": "Interface"
		},
		{
			"name": "Freja",
			"packageName": "freja",
			"type": "Class"
		},
		{
			"name": "LibraryScanner",
			"packageName": "freja.scan",
			"type": "Class"
		},
		{
			"name": "LibraryScanner.Listener",
			"packageName": "freja.scan",
			"type": "Interface"
		},
		{
			"name": "LibraryScanner.State",
			"packageName": "freja.scan",
			"type": "Record"
		},
		{
			"name": "ARXTextField",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ChooserLocations",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXLabel",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXNavList",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXNavList.Row",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXSettingsPane",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXSettingsPane.Section",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXSettingsPane.Block",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXSettingsPane.Card",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXSettingsPane.Row",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXFileChooser",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXCheckBox",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXSearchField",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXMenu",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXButton",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXButton.Variant",
			"packageName": "laevatain",
			"type": "Enum"
		},
		{
			"name": "ARXLang",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXLang.Sentence",
			"packageName": "laevatain",
			"type": "Record"
		},
		{
			"name": "ARXLang.Language",
			"packageName": "laevatain",
			"type": "Record"
		},
		{
			"name": "ARXToggle",
			"packageName": "laevatain",
			"type": "Class"
		},
		{
			"name": "ARXMenuItem",
			"packageName": "laevatain",
			"type": "Class"
		}
	]
}