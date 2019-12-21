read -p "Enter Area/Region to prepend: " area

filename_counts_exceeding_31_chars=$(find * -maxdepth 1 -type f -not -name "*.tz" -not -name "prepend_Area_and_append_.tz_extension.bash" | sed 's/^/'"$area"'_/;s/$/.tz/' | awk '{if (length($0) > 31) print length($0)  " " $0}' | wc -l)

if [[ "$filename_counts_exceeding_31_chars" -eq 0 ]]; then
	find . -maxdepth 1 -type f -not -name "*.tz" -not -name "prepend_Area_and_append_.tz_extension.bash" | sed -r 's/^\.\/(.*)$/mv -v \1 '"$area"'_\1.tz/' | while read -r line || [[ -n "$line" ]]; do eval "$line"; done
else
	echo
	find * -maxdepth 1 -type f -not -name "*.tz" -not -name "prepend_Area_and_append_.tz_extension.bash" | sed 's/^/'"$area"'_/;s/$/.tz/' | awk '{if (length($0) > 31) print length($0)  " " $0}'
	
	echo -e "\nThere are atleast more than 1 filenames exceeding 31 chars limit of ESP's SPIFFS"
	read -p "Please fix the 31 chars length issue of above files manually" pause_var
fi